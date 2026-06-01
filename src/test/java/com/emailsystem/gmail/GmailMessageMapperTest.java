package com.emailsystem.gmail;

import com.emailsystem.provider.FetchedMessage;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GmailMessageMapperTest {

    private static String b64url(String s) {
        return Base64.getUrlEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsHeadersBodyAndDate() {
        Message message = new Message()
                .setId("abc123")
                .setInternalDate(1_700_000_000_000L)
                .setPayload(new MessagePart()
                        .setMimeType("multipart/alternative")
                        .setHeaders(List.of(
                                new MessagePartHeader().setName("From").setValue("Bob <bob@example.com>"),
                                new MessagePartHeader().setName("To").setValue("alice@example.com"),
                                new MessagePartHeader().setName("Subject").setValue("Hello there")))
                        .setParts(List.of(
                                new MessagePart().setMimeType("text/plain")
                                        .setBody(new MessagePartBody().setData(b64url("Plain body text"))),
                                new MessagePart().setMimeType("text/html")
                                        .setBody(new MessagePartBody().setData(b64url("<p>Hello</p>"))))));

        FetchedMessage fm = GmailMessageMapper.toFetchedMessage(message);

        assertThat(fm.externalId()).isEqualTo("abc123");
        assertThat(fm.sender()).isEqualTo("Bob <bob@example.com>");
        assertThat(fm.recipients()).isEqualTo("alice@example.com");
        assertThat(fm.subject()).isEqualTo("Hello there");
        assertThat(fm.body()).isEqualTo("Plain body text");
        assertThat(fm.bodyHtml()).isEqualTo("<p>Hello</p>");
        assertThat(fm.preview()).isEqualTo("Plain body text");
        assertThat(fm.receivedAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void fallsBackToStrippedHtmlAndDefaultsWhenMissing() {
        Message message = new Message()
                .setId("html-only")
                .setPayload(new MessagePart()
                        .setMimeType("text/html")
                        .setHeaders(List.of())
                        .setBody(new MessagePartBody().setData(b64url("<p>Hi <b>world</b></p>"))));

        FetchedMessage fm = GmailMessageMapper.toFetchedMessage(message);

        assertThat(fm.sender()).isEqualTo("(unknown)");
        assertThat(fm.subject()).isEqualTo("(no subject)");
        assertThat(fm.body()).isEqualTo("Hi world");
        assertThat(fm.receivedAt()).isNotNull();
    }
}
