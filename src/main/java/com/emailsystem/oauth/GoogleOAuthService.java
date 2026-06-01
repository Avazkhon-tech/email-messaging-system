package com.emailsystem.oauth;

import com.emailsystem.account.AccountStatus;
import com.emailsystem.account.AuthType;
import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.account.Provider;
import com.emailsystem.common.exception.BadRequestException;
import com.emailsystem.config.AppProperties;
import com.emailsystem.config.CacheNames;
import com.emailsystem.crypto.CredentialCipher;
import com.emailsystem.gmail.GmailClientFactory;
import com.emailsystem.gmail.GmailWatchService;
import com.emailsystem.security.AuthUser;
import com.emailsystem.security.JwtService;
import com.emailsystem.sync.AccountSyncWorker;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.gmail.Gmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final AppProperties properties;
    private final JwtService jwtService;
    private final GmailClientFactory clientFactory;
    private final GmailWatchService watchService;
    private final AccountSyncWorker syncWorker;
    private final EmailAccountRepository accountRepository;
    private final CredentialCipher cipher;
    private final CacheManager cacheManager;

    public String buildAuthorizationUrl(AuthUser user) {
        requireEnabled();
        AppProperties.Google g = properties.getGoogle();
        return clientFactory.authorizationFlow()
                .newAuthorizationUrl()
                .setRedirectUri(g.getRedirectUri())
                .setState(jwtService.generateOAuthState(user.id()))
                .set("prompt", "consent")
                .set("access_type", "offline")
                .set("include_granted_scopes", "true")
                .build();
    }

    public String handleCallback(String code, String state, String error) {
        String base = properties.getGoogle().getSuccessRedirect();
        if (error != null && !error.isBlank()) {
            log.warn("Gmail OAuth consent returned error: {}", error);
            return base + "?gmail=denied";
        }
        try {
            requireEnabled();
            Long userId = jwtService.parseOAuthState(state);
            connect(userId, code);
            return base + "?gmail=connected";
        } catch (Exception e) {
            log.warn("Gmail OAuth callback failed: {}", e.getMessage());
            return base + "?gmail=error";
        }
    }

    private void connect(Long userId, String code) throws Exception {
        AppProperties.Google g = properties.getGoogle();

        GoogleTokenResponse token = clientFactory.authorizationFlow()
                .newTokenRequest(code)
                .setRedirectUri(g.getRedirectUri())
                .execute();
        String refreshToken = token.getRefreshToken();
        if (refreshToken == null) {
            throw new BadRequestException("Google did not return a refresh token; remove app access at "
                    + "myaccount.google.com and reconnect");
        }

        Gmail gmail = clientFactory.forRefreshToken(refreshToken);
        String email = gmail.users().getProfile("me").execute().getEmailAddress().toLowerCase();

        EmailAccount account = upsert(userId, email, refreshToken);
        watchService.startWatch(account);
        syncWorker.sync(account);
        evictAccountsCache(userId);
        log.info("Connected Gmail account {} for user {}", account.getEmailAddress(), userId);
    }

    private EmailAccount upsert(Long userId, String email, String refreshToken) {
        EmailAccount account = accountRepository
                .findByProviderAndEmailAddress(Provider.GMAIL, email)
                .orElseGet(() -> EmailAccount.builder()
                        .userId(userId)
                        .provider(Provider.GMAIL)
                        .emailAddress(email)
                        .authType(AuthType.OAUTH)
                        .build());

        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("This Gmail account is connected to another user");
        }
        account.setAuthType(AuthType.OAUTH);
        account.setStatus(AccountStatus.ACTIVE);
        if (refreshToken != null) {
            account.setCredentials(cipher.encrypt(refreshToken));
        }
        return accountRepository.save(account);
    }

    private void requireEnabled() {
        if (!properties.getGoogle().isEnabled()) {
            throw new BadRequestException("Gmail integration is not enabled on this server");
        }
    }

    private void evictAccountsCache(Long userId) {
        Cache cache = cacheManager.getCache(CacheNames.ACCOUNTS);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
