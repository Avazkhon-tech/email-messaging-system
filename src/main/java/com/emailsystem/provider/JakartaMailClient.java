package com.emailsystem.provider;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.common.exception.ProviderException;
import com.emailsystem.config.AppProperties;
import com.emailsystem.crypto.CredentialCipher;
import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JakartaMailClient implements EmailProviderClient {

    private static final int PREVIEW_LENGTH = 200;

    private final CredentialCipher cipher;
    private final ProviderEndpointResolver endpointResolver;
    private final String timeoutMs;

    public JakartaMailClient(CredentialCipher cipher,
                             ProviderEndpointResolver endpointResolver,
                             AppProperties properties) {
        this.cipher = cipher;
        this.endpointResolver = endpointResolver;
        this.timeoutMs = String.valueOf(properties.getMail().getTimeoutMs());
    }

    @Override
    public void verifyConnection(EmailAccount account) {
        MailEndpoints ep = endpointResolver.resolve(account.getProvider());
        String password = cipher.decrypt(account.getCredentials());
        try (Store store = openStore(ep, account.getEmailAddress(), password)) {

            log.debug("IMAP connection verified for {}", account.getEmailAddress());
        } catch (Exception e) {
            throw new ProviderException("Could not connect to mailbox: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FetchedMessage> fetch(EmailAccount account, Instant since, int limit) {
        MailEndpoints ep = endpointResolver.resolve(account.getProvider());
        String password = cipher.decrypt(account.getCredentials());

        try (Store store = openStore(ep, account.getEmailAddress(), password)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                Message[] messages = (since == null)
                        ? inbox.getMessages()
                        : inbox.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(since)));

                List<Message> ordered = new ArrayList<>(Arrays.asList(messages));
                ordered.sort((a, b) -> Long.compare(sentMillis(b), sentMillis(a)));
                if (ordered.size() > limit) {
                    ordered = ordered.subList(0, limit);
                }

                List<FetchedMessage> result = new ArrayList<>(ordered.size());
                for (Message m : ordered) {
                    result.add(toFetchedMessage(m));
                }
                return result;
            } finally {
                inbox.close(false);
            }
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("Failed to fetch messages: " + e.getMessage(), e);
        }
    }

    @Override
    @Retryable(retryFor = ProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void send(EmailAccount account, OutgoingMessage message) {
        MailEndpoints ep = endpointResolver.resolve(account.getProvider());
        String password = cipher.decrypt(account.getCredentials());

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(ep.useSsl()));
        props.put("mail.smtp.host", ep.smtpHost());
        props.put("mail.smtp.port", String.valueOf(ep.smtpPort()));
        props.put("mail.smtp.connectiontimeout", timeoutMs);
        props.put("mail.smtp.timeout", timeoutMs);
        props.put("mail.smtp.writetimeout", timeoutMs);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account.getEmailAddress(), password);
            }
        });

        try {
            MimeMessage mime = new MimeMessage(session);
            mime.setFrom(new InternetAddress(account.getEmailAddress()));
            for (String to : message.recipients()) {
                mime.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
            mime.setSubject(message.subject());

            if (message.html()) {
                mime.setContent(message.body(), "text/html; charset=utf-8");
            } else {
                mime.setText(message.body(), "utf-8");
            }

            Transport.send(mime);
            log.info("Sent email from {} to {}", account.getEmailAddress(),
                    String.join(",", message.recipients()));
        } catch (Exception e) {
            throw new ProviderException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private Store openStore(MailEndpoints ep, String username, String password) {
        String protocol = ep.useSsl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", ep.imapHost());
        props.put("mail." + protocol + ".port", String.valueOf(ep.imapPort()));
        props.put("mail." + protocol + ".ssl.enable", String.valueOf(ep.useSsl()));
        props.put("mail." + protocol + ".connectiontimeout", timeoutMs);
        props.put("mail." + protocol + ".timeout", timeoutMs);

        Session session = Session.getInstance(props);
        try {
            Store store = session.getStore(protocol);
            store.connect(ep.imapHost(), ep.imapPort(), username, password);
            return store;
        } catch (Exception e) {
            throw new ProviderException("IMAP authentication failed: " + e.getMessage(), e);
        }
    }

    private long sentMillis(Message m) {
        try {
            Date d = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
            return d != null ? d.getTime() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private FetchedMessage toFetchedMessage(Message m) throws Exception {
        String externalId = externalId(m);
        String sender = m.getFrom() != null && m.getFrom().length > 0
                ? m.getFrom()[0].toString() : "(unknown)";
        String recipients = m.getRecipients(Message.RecipientType.TO) == null ? ""
                : Arrays.stream(m.getRecipients(Message.RecipientType.TO))
                        .map(Object::toString).collect(Collectors.joining(", "));
        String subject = m.getSubject() != null ? m.getSubject() : "(no subject)";
        Date received = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
        Instant receivedAt = received != null ? received.toInstant() : Instant.now();

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        extractParts(m, text, html);

        String body = !text.isEmpty() ? text.toString() : stripHtml(html.toString());
        String preview = buildPreview(body);

        return new FetchedMessage(externalId, sender, recipients, subject,
                body, html.toString(), preview, receivedAt);
    }

    private String externalId(Message m) throws Exception {
        String[] header = m.getHeader("Message-ID");
        if (header != null && header.length > 0 && header[0] != null) {
            return header[0];
        }

        Date d = m.getSentDate();
        return "synthetic:" + (m.getSubject() != null ? m.getSubject().hashCode() : 0)
                + ":" + (d != null ? d.getTime() : 0);
    }

    private void extractParts(Part part, StringBuilder text, StringBuilder html) throws Exception {
        if (part.isMimeType("text/plain")) {
            text.append(asString(part.getContent()));
        } else if (part.isMimeType("text/html")) {
            html.append(asString(part.getContent()));
        } else if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bp = multipart.getBodyPart(i);
                extractParts(bp, text, html);
            }
        }
    }

    private String asString(Object content) {
        return content != null ? content.toString() : "";
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildPreview(String body) {
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= PREVIEW_LENGTH
                ? collapsed
                : collapsed.substring(0, PREVIEW_LENGTH) + "…";
    }
}
