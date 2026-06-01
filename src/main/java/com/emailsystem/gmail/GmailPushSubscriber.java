package com.emailsystem.gmail;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.AuthType;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.account.Provider;
import com.emailsystem.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailPushSubscriber implements SmartLifecycle {

    private final AppProperties properties;
    private final EmailAccountRepository accountRepository;
    private final GmailHistorySync historySync;
    private final ObjectMapper objectMapper;

    private Subscriber subscriber;
    private volatile boolean running;

    @Override
    public void start() {
        AppProperties.Google g = properties.getGoogle();
        if (!g.isEnabled()) {
            log.info("Gmail push disabled (app.google.enabled=false); Pub/Sub subscriber not started");
            return;
        }
        ProjectSubscriptionName subscription =
                ProjectSubscriptionName.of(g.getProjectId(), g.getPubsubSubscription());
        MessageReceiver receiver = this::receive;
        subscriber = Subscriber.newBuilder(subscription, receiver).build();
        subscriber.startAsync().awaitRunning();
        running = true;
        log.info("Gmail Pub/Sub subscriber started on {}", subscription);
    }

    private void receive(PubsubMessage message, AckReplyConsumer reply) {
        try {
            handle(message);
            reply.ack();
        } catch (Exception e) {
            log.warn("Failed to process Gmail Pub/Sub message {}: {}",
                    message.getMessageId(), e.getMessage());
            reply.nack();
        }
    }

    private void handle(PubsubMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getData().toStringUtf8());
        String email = node.path("emailAddress").asText("");
        String historyText = node.path("historyId").asText("");
        if (email.isEmpty() || historyText.isEmpty()) {
            log.warn("Ignoring malformed Gmail notification: {}", message.getData().toStringUtf8());
            return;
        }
        BigInteger historyId = new BigInteger(historyText);

        Optional<EmailAccount> account = accountRepository
                .findByProviderAndEmailAddress(Provider.GMAIL, email.toLowerCase());
        account.filter(a -> a.getAuthType() == AuthType.OAUTH && a.getStatus() == AccountStatus.ACTIVE)
                .ifPresentOrElse(
                        a -> historySync.syncFromHistory(a, historyId),
                        () -> log.debug("No active OAuth Gmail account for {}", email));
    }

    @Override
    public void stop() {
        if (subscriber != null) {
            subscriber.stopAsync().awaitTerminated();
            log.info("Gmail Pub/Sub subscriber stopped");
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
