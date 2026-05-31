package com.emailsystem.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "email_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_message_id", nullable = false, length = 512)
    private String externalMessageId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    private String sender;

    @Column(columnDefinition = "text")
    private String recipients;

    @Column(length = 1024)
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(length = 512)
    private String preview;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "read_status", nullable = false)
    private boolean readStatus;
}
