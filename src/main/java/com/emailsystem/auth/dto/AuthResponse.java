package com.emailsystem.auth.dto;

public record AuthResponse(
        String token,
        Long userId,
        String fullname,
        String email
) {
}
