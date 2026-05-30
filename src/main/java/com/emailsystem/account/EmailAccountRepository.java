package com.emailsystem.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    List<EmailAccount> findByUserId(Long userId);

    Optional<EmailAccount> findByIdAndUserId(Long id, Long userId);

    List<EmailAccount> findByStatus(AccountStatus status);

    boolean existsByUserIdAndEmailAddress(Long userId, String emailAddress);
}
