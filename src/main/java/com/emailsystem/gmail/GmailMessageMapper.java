package com.emailsystem.gmail;

import com.emailsystem.provider.FetchedMessage;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

final class GmailMessageMapper {

    private static final int PREVIEW_LENGTH = 200;

    private GmailMessageMapper() {
    }

    static FetchedMessage toFetchedMessage(Message message) {
        MessagePart payload = message.getPayload();
        String sender = header(payload, "From");
        String recipients = header(payload, "To");
        String subject = header(payload, "Subject");
        if (subject.isEmpty()) {
            subject = "(no subject)";
        }
        if (sender.isEmpty()) {
            sender = "(unknown)";
        }

        Instant receivedAt = message.getInternalDate() != null
                ? Instant.ofEpochMilli(message.getInternalDate())
                : Instant.now();

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        walk(payload, text, html);

        String body = !text.isEmpty() ? text.toString() : stripHtml(html.toString());
        String preview = buildPreview(body);

        return new FetchedMessage(
                message.getId(), sender, recipients, subject, body, html.toString(), preview, receivedAt);
    }

    private static void walk(MessagePart part, StringBuilder text, StringBuilder html) {
        if (part == null) {
            return;
        }
        String mimeType = part.getMimeType() != null ? part.getMimeType() : "";
        if (mimeType.equals("text/plain")) {
            text.append(decode(part));
        } else if (mimeType.equals("text/html")) {
            html.append(decode(part));
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                walk(child, text, html);
            }
        }
    }

    private static String decode(MessagePart part) {
        if (part.getBody() == null || part.getBody().getData() == null) {
            return "";
        }
        byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static String header(MessagePart payload, String name) {
        if (payload == null || payload.getHeaders() == null) {
            return "";
        }
        List<MessagePartHeader> headers = payload.getHeaders();
        for (MessagePartHeader h : headers) {
            if (name.equalsIgnoreCase(h.getName())) {
                return h.getValue() != null ? h.getValue() : "";
            }
        }
        return "";
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String buildPreview(String body) {
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= PREVIEW_LENGTH
                ? collapsed
                : collapsed.substring(0, PREVIEW_LENGTH) + "…";
    }
}
