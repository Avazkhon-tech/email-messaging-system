package com.emailsystem.sync;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.config.AppProperties;
import com.emailsystem.message.EmailMessage;
import com.emailsystem.message.EmailMessageRepository;
import com.emailsystem.message.MessageMapper;
import com.emailsystem.message.dto.MessageSummaryResponse;
import com.emailsystem.provider.EmailProviderClient;
import com.emailsystem.provider.FetchedMessage;
import com.emailsystem.realtime.NewMailEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AccountSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(AccountSyncWorker.class);

    private final EmailAccountRepository accountRepository;
    private final EmailMessageRepository messageRepository;
    private final EmailProviderClient providerClient;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties properties;
    private final MessageMapper mapper;

    @Transactional
    public void sync(EmailAccount account) {
        Instant syncStartedAt = Instant.now();
        try {
            List<FetchedMessage> fetched = providerClient.fetch(
                    account, account.getLastSyncedAt(), properties.getSync().getFetchLimit());

            List<MessageSummaryResponse> newSummaries = new ArrayList<>();
            for (FetchedMessage fm : fetched) {
                if (messageRepository.existsByAccountIdAndExternalMessageId(
                        account.getId(), fm.externalId())) {
                    continue;
                }
                EmailMessage saved = messageRepository.save(mapper.toEntity(fm, account.getId()));
                newSummaries.add(mapper.toSummary(saved));
            }

            account.setLastSyncedAt(syncStartedAt);
            account.setLastSyncStatus("OK: " + newSummaries.size() + " new");
            accountRepository.save(account);

            if (!newSummaries.isEmpty()) {

                eventPublisher.publishEvent(
                        new NewMailEvent(account.getUserId(), account.getId(), newSummaries));
                log.info("Account {} synced: {} new message(s)",
                        account.getEmailAddress(), newSummaries.size());
            }
        } catch (Exception e) {

            account.setLastSyncStatus("ERROR: " + truncate(e.getMessage()));
            accountRepository.save(account);
            log.warn("Sync failed for account {}: {}", account.getEmailAddress(), e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "unknown error";
        }
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
