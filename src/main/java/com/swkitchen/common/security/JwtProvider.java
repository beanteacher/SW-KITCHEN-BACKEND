package com.swkitchen.common.security;

import com.swkitchen.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtProvider(SecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
    }

    public String createAccessToken(Long accountId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(accountId.toString())
            .claim(ROLE_CLAIM, role.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)))
            .signWith(key)
            .compact();
    }

    /** 서명이 틀리거나 만료됐거나 내용이 이상하면 빈 값 */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenClaims(
                Long.valueOf(claims.getSubject()), Role.valueOf(claims.get(ROLE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public record TokenClaims(Long accountId, Role role) {}
}
