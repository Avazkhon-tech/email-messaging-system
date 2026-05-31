package com.emailsystem.sync;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MailSyncService {

    private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);

    private final EmailAccountRepository accountRepository;
    private final AccountSyncWorker worker;

    public MailSyncService(EmailAccountRepository accountRepository, AccountSyncWorker worker) {
        this.accountRepository = accountRepository;
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString = "${app.sync.interval-ms}",
            initialDelayString = "${app.sync.initial-delay-ms}")
    public void syncAllActiveAccounts() {
        List<EmailAccount> active = accountRepository.findByStatus(AccountStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        log.debug("Starting sync run for {} active account(s)", active.size());
        for (EmailAccount account : active) {
            worker.sync(account);
        }
    }
}
