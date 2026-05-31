package com.emailsystem.crypto;

import com.emailsystem.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private CredentialCipher cipher;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();

        props.getCrypto().setSecret(
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        cipher = new CredentialCipher(props);
    }

    @Test
    void encryptThenDecryptReturnsOriginal() {
        String secret = "my-gmail-app-password";
        String encrypted = cipher.encrypt(secret);

        assertThat(encrypted).isNotEqualTo(secret);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void encryptionIsNonDeterministicDueToRandomIv() {
        String secret = "same-input";
        assertThat(cipher.encrypt(secret)).isNotEqualTo(cipher.encrypt(secret));
    }

    @Test
    void decryptingTamperedDataFails() {
        String encrypted = cipher.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeyOfInvalidLength() {
        AppProperties bad = new AppProperties();
        bad.getCrypto().setSecret(Base64.getEncoder().encodeToString("short".getBytes()));
        assertThatThrownBy(() -> new CredentialCipher(bad))
                .isInstanceOf(IllegalStateException.class);
    }
}
