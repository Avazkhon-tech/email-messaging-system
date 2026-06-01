package com.emailsystem.gmail;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.common.exception.ProviderException;
import com.emailsystem.provider.EmailProviderClient;
import com.emailsystem.provider.FetchedMessage;
import com.emailsystem.provider.OutgoingMessage;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailApiClient implements EmailProviderClient {

    private static final String ME = "me";

    private final GmailClientFactory clientFactory;

    @Override
    public void verifyConnection(EmailAccount account) {
        try {
            clientFactory.forAccount(account).users().getProfile(ME).execute();
            log.debug("Gmail API connection verified for {}", account.getEmailAddress());
        } catch (IOException e) {
            throw new ProviderException("Could not connect to Gmail: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FetchedMessage> fetch(EmailAccount account, Instant since, int limit) {
        Gmail gmail = clientFactory.forAccount(account);
        try {
            Gmail.Users.Messages.List request = gmail.users().messages().list(ME)
                    .setMaxResults((long) limit)
                    .setLabelIds(List.of("INBOX"));
            if (since != null) {
                request.setQ("after:" + since.getEpochSecond());
            }
            ListMessagesResponse response = request.execute();

            List<FetchedMessage> result = new ArrayList<>();
            if (response.getMessages() != null) {
                for (Message ref : response.getMessages()) {
                    result.add(getMessage(gmail, ref.getId()));
                }
            }
            return result;
        } catch (IOException e) {
            throw new ProviderException("Failed to fetch Gmail messages: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(EmailAccount account, OutgoingMessage message) {
        try {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
            mime.setFrom(new InternetAddress(account.getEmailAddress()));
            for (String to : message.recipients()) {
                mime.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
            }
            mime.setSubject(message.subject());
            if (message.html()) {
                mime.setContent(message.body(), "text/html; charset=utf-8");
            } else {
                mime.setText(message.body(), "utf-8");
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mime.writeTo(buffer);
            String raw = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

            Message gmailMessage = new Message().setRaw(raw);
            clientFactory.forAccount(account).users().messages().send(ME, gmailMessage).execute();
            log.info("Sent Gmail message from {} to {}", account.getEmailAddress(),
                    String.join(",", message.recipients()));
        } catch (Exception e) {
            throw new ProviderException("Failed to send Gmail message: " + e.getMessage(), e);
        }
    }

    FetchedMessage getMessage(Gmail gmail, String messageId) throws IOException {
        Message full = gmail.users().messages().get(ME, messageId).setFormat("full").execute();
        return GmailMessageMapper.toFetchedMessage(full);
    }
}
