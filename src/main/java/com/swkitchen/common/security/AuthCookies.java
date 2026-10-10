package com.swkitchen.common.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 토큰은 자바스크립트가 읽지 못하는 쿠키로만 주고받는다 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    // 리프레시 토큰은 인증 API 에만 실려 가게 경로를 좁힌다
    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final SecurityProperties properties;

    public ResponseCookie accessToken(String token) {
        return build(ACCESS_TOKEN, token, ACCESS_PATH, properties.jwt().accessTokenTtl());
    }

    public ResponseCookie refreshToken(String token) {
        return build(REFRESH_TOKEN, token, REFRESH_PATH, properties.jwt().refreshTokenTtl());
    }

    public ResponseCookie clearAccessToken() {
        return build(ACCESS_TOKEN, "", ACCESS_PATH, Duration.ZERO);
    }

    public ResponseCookie clearRefreshToken() {
        return build(REFRESH_TOKEN, "", REFRESH_PATH, Duration.ZERO);
    }

    private static ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build();
    }
}
