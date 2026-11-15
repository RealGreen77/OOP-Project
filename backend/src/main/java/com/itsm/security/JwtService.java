package com.itsm.security;

import com.itsm.config.ItsmProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HMAC-SHA256 signed JSON Web Tokens.
 * Tokens carry only the user id, email and role; permissions are re-derived from the database on every
 * request, so deactivating a user or changing their role takes effect immediately.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;
    private final String issuer;
    private final Clock clock;

    public JwtService(ItsmProperties properties, Clock clock) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("itsm.jwt.secret must be at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.lifetime = Duration.ofMinutes(properties.jwt().expirationMinutes());
        this.issuer = properties.jwt().issuer();
        this.clock = clock;
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public IssuedToken issue(UserPrincipal principal) {
        Instant now = clock.instant();
        Instant expires = now.plus(lifetime);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(principal.getId()))
                .claim("email", principal.getUsername())
                .claim("role", principal.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expires);
    }

    /** @return the user id if the token is authentic, unexpired and from this issuer */
    public Optional<Long> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
