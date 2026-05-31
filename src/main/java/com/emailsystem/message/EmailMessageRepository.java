package com.emailsystem.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    boolean existsByAccountIdAndExternalMessageId(Long accountId, String externalMessageId);

    @Query("""
            SELECT m FROM EmailMessage m
            WHERE m.accountId IN (SELECT a.id FROM EmailAccount a WHERE a.userId = :userId)
            """)
    Page<EmailMessage> findForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT m FROM EmailMessage m
            WHERE m.accountId IN (SELECT a.id FROM EmailAccount a WHERE a.userId = :userId)
              AND (LOWER(m.subject) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(m.sender) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<EmailMessage> searchForUser(@Param("userId") Long userId,
                                     @Param("search") String search,
                                     Pageable pageable);

    @Query("""
            SELECT m FROM EmailMessage m
            WHERE m.id = :id
              AND m.accountId IN (SELECT a.id FROM EmailAccount a WHERE a.userId = :userId)
            """)
    Optional<EmailMessage> findByIdForUser(@Param("id") Long id, @Param("userId") Long userId);
}
