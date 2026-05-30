package com.emailsystem.account.dto;

import com.emailsystem.account.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Status is required")
        AccountStatus status
) {
}
