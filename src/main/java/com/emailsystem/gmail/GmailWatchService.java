package com.emailsystem.gmail;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.AuthType;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.account.Provider;
import com.emailsystem.common.exception.ProviderException;
import com.emailsystem.config.AppProperties;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailWatchService {

    private final GmailClientFactory clientFactory;
    private final EmailAccountRepository accountRepository;
    private final AppProperties properties;

    @Transactional
    public void startWatch(EmailAccount account) {
        WatchRequest request = new WatchRequest()
                .setTopicName(properties.getGoogle().topicName())
                .setLabelIds(List.of("INBOX"));
        try {
            WatchResponse response = clientFactory.forAccount(account)
                    .users().watch("me", request).execute();
            account.setHistoryId(String.valueOf(response.getHistoryId()));
            account.setWatchExpiration(response.getExpiration() != null
                    ? Instant.ofEpochMilli(response.getExpiration()) : null);
            accountRepository.save(account);
            log.info("Gmail watch registered for {} (historyId={}, expires {})",
                    account.getEmailAddress(), account.getHistoryId(), account.getWatchExpiration());
        } catch (IOException e) {
            throw new ProviderException("Failed to start Gmail watch: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void stopWatch(EmailAccount account) {
        try {
            clientFactory.forAccount(account).users().stop("me").execute();
            log.info("Gmail watch stopped for {}", account.getEmailAddress());
        } catch (Exception e) {
            log.warn("Failed to stop Gmail watch for {}: {}", account.getEmailAddress(), e.getMessage());
        }
        account.setWatchExpiration(null);
        accountRepository.save(account);
    }

    @Scheduled(cron = "${app.google.watch-renew-cron:0 0 */6 * * *}")
    public void renewExpiringWatches() {
        if (!properties.getGoogle().isEnabled()) {
            return;
        }
        Instant threshold = Instant.now().plus(Duration.ofDays(1));
        List<EmailAccount> accounts = accountRepository.findByProviderAndStatusAndAuthType(
                Provider.GMAIL, AccountStatus.ACTIVE, AuthType.OAUTH);
        for (EmailAccount account : accounts) {
            if (account.getWatchExpiration() == null || account.getWatchExpiration().isBefore(threshold)) {
                try {
                    startWatch(account);
                } catch (Exception e) {
                    log.warn("Watch renewal failed for {}: {}", account.getEmailAddress(), e.getMessage());
                }
            }
        }
    }
}
