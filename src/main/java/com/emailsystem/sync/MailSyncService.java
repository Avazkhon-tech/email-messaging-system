package com.emailsystem.sync;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailSyncService {

    private final EmailAccountRepository accountRepository;
    private final AccountSyncWorker worker;

    @Scheduled(
            fixedDelayString = "${app.sync.interval-ms}",
            initialDelayString = "${app.sync.initial-delay-ms}")
    public void syncAllActiveAccounts() {
        List<EmailAccount> active = accountRepository.findActiveAccountsNeedingSync(Instant.now().minusSeconds(60));
        log.info("Active account count: {}", active.size());
        if (active.isEmpty()) {
            return;
        }
        log.debug("Starting sync run for {} active account(s)", active.size());
        for (EmailAccount account : active) {
            worker.sync(account);
        }
    }
}
