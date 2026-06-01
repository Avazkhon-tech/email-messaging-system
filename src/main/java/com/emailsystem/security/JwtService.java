package com.emailsystem.security;

import com.emailsystem.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(AppProperties properties) {
        byte[] secret = Base64.getDecoder().decode(properties.getJwt().getSecret());
        this.key = Keys.hmacShaKeyFor(secret);
        this.expirationMs = properties.getJwt().getExpirationMs();
    }

    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String generateOAuthState(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("purpose", "oauth_state")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 600_000L))
                .signWith(key)
                .compact();
    }

    public Long parseOAuthState(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"oauth_state".equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Not an OAuth state token");
        }
        return Long.valueOf(claims.getSubject());
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        return new AuthUser(userId, email);
    }
}
