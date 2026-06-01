package com.emailsystem.gmail;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.common.exception.ProviderException;
import com.emailsystem.config.AppProperties;
import com.emailsystem.crypto.CredentialCipher;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
@RequiredArgsConstructor
public class GmailClientFactory {

    public static final String APPLICATION_NAME = "EmailMessagingSystem";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final AppProperties properties;
    private final CredentialCipher cipher;

    public Gmail forAccount(EmailAccount account) {
        return forRefreshToken(cipher.decrypt(account.getCredentials()));
    }

    public Gmail forRefreshToken(String refreshToken) {
        AppProperties.Google g = properties.getGoogle();
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(g.getClientId())
                .setClientSecret(g.getClientSecret())
                .setRefreshToken(refreshToken)
                .build();
        return new Gmail.Builder(transport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public GoogleAuthorizationCodeFlow authorizationFlow() {
        AppProperties.Google g = properties.getGoogle();
        return new GoogleAuthorizationCodeFlow.Builder(
                transport(), JSON_FACTORY, g.getClientId(), g.getClientSecret(), g.getScopes())
                .setAccessType("offline")
                .build();
    }

    public static JsonFactory jsonFactory() {
        return JSON_FACTORY;
    }

    private NetHttpTransport transport() {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new ProviderException("Could not initialize Google HTTP transport: " + e.getMessage(), e);
        }
    }
}
