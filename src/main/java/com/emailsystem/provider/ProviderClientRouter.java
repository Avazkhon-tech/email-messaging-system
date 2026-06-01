package com.emailsystem.provider;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.Provider;
import com.emailsystem.gmail.GmailApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Primary
@Component
@RequiredArgsConstructor
public class ProviderClientRouter implements EmailProviderClient {

    private final GmailApiClient gmailApiClient;
    private final JakartaMailClient jakartaMailClient;

    private EmailProviderClient delegateFor(EmailAccount account) {
        return account.getProvider() == Provider.GMAIL ? gmailApiClient : jakartaMailClient;
    }

    @Override
    public void verifyConnection(EmailAccount account) {
        delegateFor(account).verifyConnection(account);
    }

    @Override
    public List<FetchedMessage> fetch(EmailAccount account, Instant since, int limit) {
        return delegateFor(account).fetch(account, since, limit);
    }

    @Override
    public void send(EmailAccount account, OutgoingMessage message) {
        delegateFor(account).send(account, message);
    }
}
