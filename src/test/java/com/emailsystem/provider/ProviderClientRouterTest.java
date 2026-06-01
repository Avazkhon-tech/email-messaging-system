package com.emailsystem.provider;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.Provider;
import com.emailsystem.gmail.GmailApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProviderClientRouterTest {

    @Mock GmailApiClient gmailApiClient;
    @Mock JakartaMailClient jakartaMailClient;
    @InjectMocks ProviderClientRouter router;

    private EmailAccount account(Provider provider) {
        return EmailAccount.builder().provider(provider).emailAddress("x@example.com").build();
    }

    @Test
    void gmailRoutesToGmailApiClient() {
        EmailAccount account = account(Provider.GMAIL);
        Instant since = Instant.now();

        router.verifyConnection(account);
        router.fetch(account, since, 10);
        router.send(account, new OutgoingMessage("x@example.com", List.of("a@b.com"), "s", "b", false));

        verify(gmailApiClient).verifyConnection(account);
        verify(gmailApiClient).fetch(account, since, 10);
        verifyNoInteractions(jakartaMailClient);
    }

    @Test
    void nonGmailRoutesToJakartaMailClient() {
        EmailAccount account = account(Provider.OUTLOOK);

        router.verifyConnection(account);
        router.send(account, new OutgoingMessage("x@example.com", List.of("a@b.com"), "s", "b", false));

        verify(jakartaMailClient).verifyConnection(account);
        verifyNoInteractions(gmailApiClient);
    }
}
