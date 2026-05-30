package com.emailsystem.account.dto;

import com.emailsystem.account.Provider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull(message = "Provider is required")
        Provider provider,

        @NotBlank(message = "Email address is required")
        @Email(message = "Invalid email format")
        String emailAddress,

        @NotBlank(message = "App password")
        String appPassword
) {
}
