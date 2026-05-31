package com.emailsystem.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    List<EmailAccount> findByUserId(Long userId);

    Optional<EmailAccount> findByIdAndUserId(Long id, Long userId);

    @Query(value = """
    SELECT *
    FROM email_accounts ea
    WHERE ea.status = 'ACTIVE'
      AND (
            ea.last_synced_at IS NULL
            OR ea.last_synced_at < :lastSyncedDate
          )
    order by ea.last_synced_at desc
    limit 100;
    """, nativeQuery = true)
    List<EmailAccount> findActiveAccountsNeedingSync(Instant lastSyncedDate);

    boolean existsByUserIdAndEmailAddress(Long userId, String emailAddress);
}
