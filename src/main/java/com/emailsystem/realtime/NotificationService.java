package com.emailsystem.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNewMail(NewMailEvent event) {
        publishNewMail(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOutgoingMessage(OutgoingMessageEvent event) {
        publishOutgoingMessage(event);
    }

    private void publishNewMail(NewMailEvent event) {
        var payload = Map.of(
                "type", "NEW_MAIL",
                "message", "New message received",
                "accountId", event.accountId(),
                "count", event.newMessages().size(),
                "messages", event.newMessages(),
                "timestamp", Instant.now().toString());
        String userId = String.valueOf(event.userId());
        messagingTemplate.convertAndSendToUser(userId, DESTINATION, payload);
        log.debug("Pushed {} new-mail notification(s) to user {}", event.newMessages().size(), userId);
    }

    private void publishOutgoingMessage(OutgoingMessageEvent event) {
        var payload = Map.of(
                "type", "MESSAGE_SENT",
                "status", event.status(),
                "message", event.message(),
                "recipients", event.recipients(),
                "subject", event.subject(),
                "timestamp", Instant.now().toString());
        String userId = String.valueOf(event.userId());
        messagingTemplate.convertAndSendToUser(userId, DESTINATION, payload);
        log.debug("Pushed message-sent notification (status={}) to user {}", event.status(), userId);
    }
}
