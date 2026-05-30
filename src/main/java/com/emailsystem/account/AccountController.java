package com.emailsystem.account;

import com.emailsystem.account.dto.AccountResponse;
import com.emailsystem.account.dto.CreateAccountRequest;
import com.emailsystem.account.dto.UpdateStatusRequest;
import com.emailsystem.security.AuthUser;
import com.emailsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> addAccount(@CurrentUser AuthUser user,
                                                      @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.addAccount(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<AccountResponse> listAccounts(@CurrentUser AuthUser user) {
        return accountService.listAccounts(user.id());
    }

    @PutMapping("/{id}/status")
    public AccountResponse updateStatus(@CurrentUser AuthUser user,
                                        @PathVariable Long id,
                                        @Valid @RequestBody UpdateStatusRequest request) {
        return accountService.updateStatus(user.id(), id, request.status());
    }
}
