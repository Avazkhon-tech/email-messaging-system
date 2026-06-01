package com.emailsystem.security;

import com.emailsystem.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceOAuthStateTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        jwtService = new JwtService(props);
    }

    @Test
    void roundTripsUserId() {
        String state = jwtService.generateOAuthState(42L);
        assertThat(jwtService.parseOAuthState(state)).isEqualTo(42L);
    }

    @Test
    void rejectsRegularAuthTokenAsState() {
        String authToken = jwtService.generateToken(7L, "user@example.com");
        assertThatThrownBy(() -> jwtService.parseOAuthState(authToken))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
