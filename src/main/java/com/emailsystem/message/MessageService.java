package com.emailsystem.message;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.common.exception.BadRequestException;
import com.emailsystem.common.exception.NotFoundException;
import com.emailsystem.message.dto.MessageDetailResponse;
import com.emailsystem.message.dto.MessageSummaryResponse;
import com.emailsystem.message.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final EmailMessageRepository messageRepository;
    private final EmailAccountRepository accountRepository;
    private final OutgoingMessageSender messageSender;
    private final MessageMapper mapper;

    @Transactional(readOnly = true)
    public void send(Long userId, SendMessageRequest request) {
        // Quick validation only
        EmailAccount account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("Email account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Cannot send from an inactive account");
        }
        // Trigger async send
        messageSender.sendAsync(userId, request);
    }

    @Transactional(readOnly = true)
    public Page<MessageSummaryResponse> inbox(Long userId, String search, Pageable pageable) {
        Page<EmailMessage> page = StringUtils.hasText(search)
                ? messageRepository.searchForUser(userId, search.trim(), pageable)
                : messageRepository.findForUser(userId, pageable);
        return page.map(mapper::toSummary);
    }

    @Transactional
    public MessageDetailResponse detail(Long userId, Long messageId) {
        EmailMessage message = messageRepository.findByIdForUser(messageId, userId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (!message.isReadStatus()) {
            message.setReadStatus(true);
        }
        return mapper.toDetail(message);
    }

}
