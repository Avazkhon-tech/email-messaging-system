package com.emailsystem.message.dto;

import java.time.Instant;

public record MessageSummaryResponse(
        Long id,
        String sender,
        String subject,
        String preview,
        Instant receivedAt,
        boolean readStatus
) {
}
