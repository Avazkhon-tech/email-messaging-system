package com.emailsystem.realtime;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
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
