package com.emailsystem.message;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.common.exception.BadRequestException;
import com.emailsystem.common.exception.NotFoundException;
import com.emailsystem.message.dto.SendMessageRequest;
import com.emailsystem.provider.EmailProviderClient;
import com.emailsystem.provider.OutgoingMessage;
import com.emailsystem.realtime.OutgoingMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutgoingMessageSender {

    private final EmailAccountRepository accountRepository;
    private final EmailProviderClient providerClient;
    private final ApplicationEventPublisher eventPublisher;

    @Async("taskExecutor")
    @Transactional(readOnly = true)
    public void sendAsync(Long userId, SendMessageRequest request) {
        try {
            EmailAccount account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new NotFoundException("Email account not found"));
            
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new BadRequestException("Cannot send from an inactive account");
            }

            OutgoingMessage outgoing = new OutgoingMessage(
                    account.getEmailAddress(),
                    request.recipients(),
                    request.subject(),
                    request.body(),
                    request.html());

            providerClient.send(account, outgoing);

            String recipients = String.join(", ", request.recipients());
            eventPublisher.publishEvent(
                    new OutgoingMessageEvent(
                            userId,
                            recipients,
                            request.subject(),
                            "SUCCESS",
                            "Message sent successfully"
                    )
            );
            log.info("Message sent successfully from {} to {}", account.getEmailAddress(), recipients);
        } catch (Exception e) {
            log.warn("Failed to send message: {}", e.getMessage(), e);
            eventPublisher.publishEvent(
                    new OutgoingMessageEvent(
                            userId,
                            String.join(", ", request.recipients()),
                            request.subject(),
                            "FAILURE",
                            "Failed to send: " + e.getMessage()
                    )
            );
        }
    }
}

