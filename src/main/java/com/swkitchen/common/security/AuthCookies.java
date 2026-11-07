package com.swkitchen.common.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 토큰·본인확인 결과는 자바스크립트가 읽지 못하는 쿠키로만 주고받는다 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String VERIFIED_IDENTITY = "verified_identity";
    public static final String OAUTH_STATE = "oauth_state";
    public static final String SOCIAL_SIGNUP = "social_signup";
    public static final String SOCIAL_REAUTH = "social_reauth";

    // 리프레시 토큰은 인증 API 에만 실려 가게 경로를 좁힌다
    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/v1/auth";
    private static final String API_PATH = "/api/v1";
    private static final String OAUTH_PATH = "/api/v1/auth/oauth";
    private static final String MEMBER_PATH = "/api/v1/member";

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

    public ResponseCookie verifiedIdentity(String sealed, Duration maxAge) {
        return build(VERIFIED_IDENTITY, sealed, API_PATH, maxAge);
    }

    public ResponseCookie clearVerifiedIdentity() {
        return build(VERIFIED_IDENTITY, "", API_PATH, Duration.ZERO);
    }

    public ResponseCookie oauthState(String sealed, Duration maxAge) {
        return build(OAUTH_STATE, sealed, OAUTH_PATH, maxAge);
    }

    public ResponseCookie clearOAuthState() {
        return build(OAUTH_STATE, "", OAUTH_PATH, Duration.ZERO);
    }

    public ResponseCookie socialSignup(String sealed, Duration maxAge) {
        return build(SOCIAL_SIGNUP, sealed, API_PATH, maxAge);
    }

    public ResponseCookie clearSocialSignup() {
        return build(SOCIAL_SIGNUP, "", API_PATH, Duration.ZERO);
    }

    public ResponseCookie socialReauth(String sealed, Duration maxAge) {
        return build(SOCIAL_REAUTH, sealed, MEMBER_PATH, maxAge);
    }

    public ResponseCookie clearSocialReauth() {
        return build(SOCIAL_REAUTH, "", MEMBER_PATH, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.isCookieSecure())
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build();
    }
}
