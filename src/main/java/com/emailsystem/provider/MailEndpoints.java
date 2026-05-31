package com.emailsystem.provider;

public record MailEndpoints(
        String imapHost,
        int imapPort,
        String smtpHost,
        int smtpPort,
        boolean useSsl
) {
}
