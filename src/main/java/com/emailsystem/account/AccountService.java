package com.emailsystem.account;

import com.emailsystem.account.dto.AccountResponse;
import com.emailsystem.account.dto.CreateAccountRequest;
import com.emailsystem.common.exception.ConflictException;
import com.emailsystem.common.exception.NotFoundException;
import com.emailsystem.crypto.CredentialCipher;
import com.emailsystem.provider.EmailProviderClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final EmailAccountRepository accountRepository;
    private final CredentialCipher cipher;
    private final EmailProviderClient providerClient;
    private final AccountMapper mapper;

    public AccountService(EmailAccountRepository accountRepository,
                          CredentialCipher cipher,
                          EmailProviderClient providerClient,
                          AccountMapper mapper) {
        this.accountRepository = accountRepository;
        this.cipher = cipher;
        this.providerClient = providerClient;
        this.mapper = mapper;
    }

    @Transactional
    public AccountResponse addAccount(Long userId, CreateAccountRequest request) {
        String emailAddress = request.emailAddress().trim().toLowerCase();
        if (accountRepository.existsByUserIdAndEmailAddress(userId, emailAddress)) {
            throw new ConflictException("This email account is already connected");
        }

        EmailAccount account = EmailAccount.builder()
                .userId(userId)
                .provider(request.provider())
                .emailAddress(emailAddress)
                .credentials(cipher.encrypt(request.appPassword()))
                .status(AccountStatus.ACTIVE)
                .build();

        providerClient.verifyConnection(account);

        return mapper.toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public AccountResponse updateStatus(Long userId, Long accountId, AccountStatus status) {
        EmailAccount account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Email account not found"));
        account.setStatus(status);
        return mapper.toResponse(accountRepository.save(account));
    }
}
