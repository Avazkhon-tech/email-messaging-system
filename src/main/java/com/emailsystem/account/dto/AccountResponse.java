package com.emailsystem.account.dto;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.Provider;

import java.time.Instant;

public record AccountResponse(
        Long id,
        Provider provider,
        String emailAddress,
        AccountStatus status,
        Instant createdAt,
        Instant lastSyncedAt,
        String lastSyncStatus
) {
}
