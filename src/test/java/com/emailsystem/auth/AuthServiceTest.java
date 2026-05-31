package com.emailsystem.auth;

import com.emailsystem.auth.dto.AuthResponse;
import com.emailsystem.auth.dto.LoginRequest;
import com.emailsystem.auth.dto.RegisterRequest;
import com.emailsystem.common.exception.ConflictException;
import com.emailsystem.common.exception.UnauthorizedException;
import com.emailsystem.security.JwtService;
import com.emailsystem.user.User;
import com.emailsystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void registerCreatesUserAndReturnsToken() {
        var request = new RegisterRequest("Jane Doe", "Jane@Example.com", "password123");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(1L, "jane@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        var request = new RegisterRequest("Jane", "jane@example.com", "password123");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User stored = User.builder().id(7L).email("a@b.com").fullname("A").password("hash").build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.generateToken(7L, "a@b.com")).thenReturn("tok");

        AuthResponse response = authService.login(new LoginRequest("a@b.com", "secret"));

        assertThat(response.token()).isEqualTo("tok");
    }

    @Test
    void loginRejectsWrongPassword() {
        User stored = User.builder().id(7L).email("a@b.com").password("hash").build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail("none@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@b.com", "x")))
                .isInstanceOf(UnauthorizedException.class);
    }
}
