package com.emailsystem.message.dto;

import java.time.Instant;

public record MessageDetailResponse(
        Long id,
        String sender,
        String recipients,
        String subject,
        String body,
        String bodyHtml,
        Instant receivedAt,
        boolean readStatus
) {
}
