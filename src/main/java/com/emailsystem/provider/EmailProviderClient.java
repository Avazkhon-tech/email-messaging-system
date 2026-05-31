package com.emailsystem.provider;

import com.emailsystem.account.EmailAccount;

import java.time.Instant;
import java.util.List;

public interface EmailProviderClient {

    void verifyConnection(EmailAccount account);

    List<FetchedMessage> fetch(EmailAccount account, Instant since, int limit);

    void send(EmailAccount account, OutgoingMessage message);
}
