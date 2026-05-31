package com.emailsystem.provider;

import com.emailsystem.account.Provider;

public enum ProviderEndpoints {

    GMAIL("imap.gmail.com", 993, "smtp.gmail.com", 587),
    OUTLOOK("outlook.office365.com", 993, "smtp.office365.com", 587),
    YAHOO("imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 587);

    private final String imapHost;
    private final int imapPort;
    private final String smtpHost;
    private final int smtpPort;

    ProviderEndpoints(String imapHost, int imapPort, String smtpHost, int smtpPort) {
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
    }

    public static ProviderEndpoints forProvider(Provider provider) {
        return switch (provider) {
            case GMAIL -> GMAIL;
            case OUTLOOK -> OUTLOOK;
            case YAHOO -> YAHOO;
        };
    }

    public String imapHost() {
        return imapHost;
    }

    public int imapPort() {
        return imapPort;
    }

    public String smtpHost() {
        return smtpHost;
    }

    public int smtpPort() {
        return smtpPort;
    }
}
