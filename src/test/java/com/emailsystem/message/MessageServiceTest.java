package com.emailsystem.message;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.account.Provider;
import com.emailsystem.common.exception.BadRequestException;
import com.emailsystem.common.exception.NotFoundException;
import com.emailsystem.message.dto.MessageDetailResponse;
import com.emailsystem.message.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock EmailMessageRepository messageRepository;
    @Mock EmailAccountRepository accountRepository;
    @Mock OutgoingMessageSender messageSender;
    @Mock MessageMapper mapper;
    @InjectMocks MessageService messageService;

    private EmailAccount activeAccount() {
        return EmailAccount.builder()
                .id(10L).userId(1L).provider(Provider.GMAIL)
                .emailAddress("me@gmail.com").credentials("enc")
                .status(AccountStatus.ACTIVE).build();
    }

    @Test
    void sendDispatchesThroughAsyncSender() {
        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(activeAccount()));
        var request = new SendMessageRequest(10L, List.of("to@x.com"), "Hi", "Body", false);

        messageService.send(1L, request);

        verify(messageSender).sendAsync(1L, request);
    }

    @Test
    void sendRejectsInactiveAccount() {
        EmailAccount inactive = activeAccount();
        inactive.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(inactive));
        var request = new SendMessageRequest(10L, List.of("to@x.com"), "Hi", "Body", false);

        assertThatThrownBy(() -> messageService.send(1L, request))
                .isInstanceOf(BadRequestException.class);
        verify(messageSender, never()).sendAsync(any(), any());
    }

    @Test
    void sendRejectsUnknownAccount() {
        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        var request = new SendMessageRequest(99L, List.of("to@x.com"), "Hi", "Body", false);

        assertThatThrownBy(() -> messageService.send(1L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void detailMarksMessageRead() {
        EmailMessage message = EmailMessage.builder()
                .id(5L).accountId(10L).subject("s").readStatus(false).build();
        when(messageRepository.findByIdForUser(5L, 1L)).thenReturn(Optional.of(message));
        MessageDetailResponse expectedResponse = new MessageDetailResponse(5L, "", "", "s", "", "", null, true);
        when(mapper.toDetail(message)).thenReturn(expectedResponse);

        MessageDetailResponse response = messageService.detail(1L, 5L);

        assertThat(response.readStatus()).isTrue();
        assertThat(message.isReadStatus()).isTrue();
    }

    @Test
    void detailRejectsMessageNotOwnedByUser() {
        when(messageRepository.findByIdForUser(eq(5L), eq(2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.detail(2L, 5L))
                .isInstanceOf(NotFoundException.class);
    }
}
