package com.emailsystem.sync;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.account.Provider;
import com.emailsystem.config.AppProperties;
import com.emailsystem.message.EmailMessage;
import com.emailsystem.message.EmailMessageRepository;
import com.emailsystem.message.MessageMapper;
import com.emailsystem.provider.EmailProviderClient;
import com.emailsystem.provider.FetchedMessage;
import com.emailsystem.realtime.NewMailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSyncWorkerTest {

    @Mock EmailAccountRepository accountRepository;
    @Mock EmailMessageRepository messageRepository;
    @Mock EmailProviderClient providerClient;
    @Mock ApplicationEventPublisher eventPublisher;

    AccountSyncWorker worker;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getSync().setFetchLimit(50);
        MessageMapper mapper = Mappers.getMapper(MessageMapper.class);
        worker = new AccountSyncWorker(accountRepository, messageRepository,
                providerClient, eventPublisher, props, mapper, new NoOpCacheManager());
    }

    private EmailAccount account() {
        return EmailAccount.builder()
                .id(10L).userId(1L).provider(Provider.GMAIL)
                .emailAddress("me@gmail.com").credentials("enc")
                .status(AccountStatus.ACTIVE).build();
    }

    private FetchedMessage fetched(String externalId) {
        return new FetchedMessage(externalId, "from@x.com", "me@gmail.com",
                "Subject " + externalId, "body", "", "preview", Instant.now());
    }

    @Test
    void persistsOnlyNewMessagesAndPublishesEvent() {
        EmailAccount account = account();
        when(providerClient.fetch(eq(account), any(), anyInt()))
                .thenReturn(List.of(fetched("new-1"), fetched("dup-1")));
        when(messageRepository.existsByAccountIdAndExternalMessageId(10L, "new-1")).thenReturn(false);
        when(messageRepository.existsByAccountIdAndExternalMessageId(10L, "dup-1")).thenReturn(true);
        when(messageRepository.save(any(EmailMessage.class))).thenAnswer(inv -> {
            EmailMessage m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        worker.sync(account);

        verify(messageRepository, times(1)).save(any(EmailMessage.class));

        ArgumentCaptor<NewMailEvent> eventCaptor = ArgumentCaptor.forClass(NewMailEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().newMessages()).hasSize(1);

        assertThat(account.getLastSyncStatus()).isEqualTo("OK: 1 new");
        assertThat(account.getLastSyncedAt()).isNotNull();
    }

    @Test
    void noEventWhenNothingNew() {
        EmailAccount account = account();
        when(providerClient.fetch(eq(account), any(), anyInt()))
                .thenReturn(List.of(fetched("dup-1")));
        when(messageRepository.existsByAccountIdAndExternalMessageId(10L, "dup-1")).thenReturn(true);

        worker.sync(account);

        verify(messageRepository, org.mockito.Mockito.never()).save(any());
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
        assertThat(account.getLastSyncStatus()).isEqualTo("OK: 0 new");
    }

    @Test
    void recordsErrorStatusWhenProviderFails() {
        EmailAccount account = account();
        when(providerClient.fetch(eq(account), any(), anyInt()))
                .thenThrow(new com.emailsystem.common.exception.ProviderException("IMAP down"));

        worker.sync(account);

        assertThat(account.getLastSyncStatus()).startsWith("ERROR:");
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
    }
}
