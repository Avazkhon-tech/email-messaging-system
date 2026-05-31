package com.emailsystem.provider;

import java.time.Instant;

public record FetchedMessage(
        String externalId,
        String sender,
        String recipients,
        String subject,
        String body,
        String bodyHtml,
        String preview,
        Instant receivedAt
) {
}
