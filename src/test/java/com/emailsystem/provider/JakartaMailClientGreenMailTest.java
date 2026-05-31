package com.emailsystem.provider;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.Provider;
import com.emailsystem.config.AppProperties;
import com.emailsystem.crypto.CredentialCipher;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JakartaMailClientGreenMailTest {

    private static final String EMAIL = "me@localhost";
    private static final String PASSWORD = "app-password";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(EMAIL, EMAIL, PASSWORD));

    private JakartaMailClient client;
    private EmailAccount account;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getCrypto().setSecret(
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        CredentialCipher cipher = new CredentialCipher(props);

        ProviderEndpointResolver resolver = provider -> new MailEndpoints(
                "127.0.0.1", greenMail.getImap().getPort(),
                "127.0.0.1", greenMail.getSmtp().getPort(),
                false);

        client = new JakartaMailClient(cipher, resolver, props);
        account = EmailAccount.builder()
                .id(1L).userId(1L).provider(Provider.GMAIL)
                .emailAddress(EMAIL).credentials(cipher.encrypt(PASSWORD))
                .status(AccountStatus.ACTIVE).build();
    }

    @Test
    void verifyConnectionSucceedsWithValidCredentials() {
        client.verifyConnection(account);
    }

    @Test
    void sendThenFetchRoundTrip() {
        OutgoingMessage outgoing = new OutgoingMessage(
                EMAIL, List.of(EMAIL), "Hello from test", "This is the body", false);

        client.send(account, outgoing);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        List<FetchedMessage> fetched = client.fetch(account, null, 50);

        assertThat(fetched).hasSize(1);
        FetchedMessage msg = fetched.get(0);
        assertThat(msg.subject()).isEqualTo("Hello from test");
        assertThat(msg.body()).contains("This is the body");
        assertThat(msg.sender()).contains(EMAIL);
        assertThat(msg.externalId()).isNotBlank();
    }
}
