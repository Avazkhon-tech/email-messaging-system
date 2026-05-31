package com.emailsystem.realtime;

public record OutgoingMessageEvent(
        Long userId,
        String recipients,
        String subject,
        String status,
        String message
) {
}

