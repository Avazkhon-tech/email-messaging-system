package com.emailsystem.message.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SendMessageRequest(
        @NotNull(message = "accountId is required")
        Long accountId,

        @NotEmpty(message = "At least one recipient is required")
        List<@Email(message = "Invalid recipient email format") @NotBlank String> recipients,

        @NotBlank(message = "Subject is required")
        String subject,

        @NotBlank(message = "Body is required")
        String body,

        boolean html
) {
}
