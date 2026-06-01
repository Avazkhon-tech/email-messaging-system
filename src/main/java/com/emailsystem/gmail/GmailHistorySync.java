package com.emailsystem.gmail;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.common.exception.ProviderException;
import com.emailsystem.config.AppProperties;
import com.emailsystem.config.CacheNames;
import com.emailsystem.message.EmailMessage;
import com.emailsystem.message.EmailMessageRepository;
import com.emailsystem.message.MessageMapper;
import com.emailsystem.message.dto.MessageSummaryResponse;
import com.emailsystem.provider.FetchedMessage;
import com.emailsystem.realtime.NewMailEvent;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailHistorySync {

    private static final String ME = "me";

    private final GmailClientFactory clientFactory;
    private final EmailAccountRepository accountRepository;
    private final EmailMessageRepository messageRepository;
    private final MessageMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties properties;
    private final CacheManager cacheManager;

    @Transactional
    public void syncFromHistory(EmailAccount account, BigInteger notifiedHistoryId) {
        Gmail gmail = clientFactory.forAccount(account);
        try {
            List<String> messageIds = (account.getHistoryId() == null)
                    ? recentInboxMessageIds(gmail)
                    : collectAddedMessageIds(gmail, new BigInteger(account.getHistoryId()));

            List<MessageSummaryResponse> newSummaries = new ArrayList<>();
            for (String id : messageIds) {
                if (messageRepository.existsByAccountIdAndExternalMessageId(account.getId(), id)) {
                    continue;
                }
                Message full = gmail.users().messages().get(ME, id).setFormat("full").execute();
                FetchedMessage fm = GmailMessageMapper.toFetchedMessage(full);
                EmailMessage saved = messageRepository.save(mapper.toEntity(fm, account.getId()));
                newSummaries.add(mapper.toSummary(saved));
            }

            account.setHistoryId(String.valueOf(notifiedHistoryId));
            account.setLastSyncedAt(Instant.now());
            account.setLastSyncStatus("OK: " + newSummaries.size() + " new (push)");
            accountRepository.save(account);
            evictAccountsCache(account.getUserId());

            if (!newSummaries.isEmpty()) {
                eventPublisher.publishEvent(
                        new NewMailEvent(account.getUserId(), account.getId(), newSummaries));
                log.info("Gmail push for {}: {} new message(s)",
                        account.getEmailAddress(), newSummaries.size());
            }
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                account.setHistoryId(null);
                accountRepository.save(account);
                log.warn("Gmail history id too old for {}; reset cursor", account.getEmailAddress());
            } else {
                throw new ProviderException("Gmail history sync failed: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new ProviderException("Gmail history sync failed: " + e.getMessage(), e);
        }
    }

    private List<String> collectAddedMessageIds(Gmail gmail, BigInteger startHistoryId) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        String pageToken = null;
        do {
            ListHistoryResponse response = gmail.users().history().list(ME)
                    .setStartHistoryId(startHistoryId)
                    .setHistoryTypes(List.of("messageAdded"))
                    .setLabelId("INBOX")
                    .setPageToken(pageToken)
                    .execute();
            if (response.getHistory() != null) {
                for (History history : response.getHistory()) {
                    if (history.getMessagesAdded() != null) {
                        for (HistoryMessageAdded added : history.getMessagesAdded()) {
                            if (added.getMessage() != null) {
                                ids.add(added.getMessage().getId());
                            }
                        }
                    }
                }
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null);
        return new ArrayList<>(ids);
    }

    private List<String> recentInboxMessageIds(Gmail gmail) throws IOException {
        ListMessagesResponse response = gmail.users().messages().list(ME)
                .setLabelIds(List.of("INBOX"))
                .setMaxResults((long) properties.getSync().getFetchLimit())
                .execute();
        List<String> ids = new ArrayList<>();
        if (response.getMessages() != null) {
            for (Message ref : response.getMessages()) {
                ids.add(ref.getId());
            }
        }
        return ids;
    }

    private void evictAccountsCache(Long userId) {
        Cache cache = cacheManager.getCache(CacheNames.ACCOUNTS);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
