package com.emailsystem.provider;

import java.util.List;

public record OutgoingMessage(
        String from,
        List<String> recipients,
        String subject,
        String body,
        boolean html
) {
}
