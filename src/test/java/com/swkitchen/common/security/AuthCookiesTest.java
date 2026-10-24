package com.swkitchen.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthCookiesTest {

    @Test
    @DisplayName("Secure 설정을 안 적으면 켜고, false 일 때만 끈다")
    void secureDefault() {
        assertThat(cookies(null).accessToken("t").isSecure()).isTrue();
        assertThat(cookies(true).refreshToken("t").isSecure()).isTrue();
        assertThat(cookies(false).accessToken("t").isSecure()).isFalse();
        assertThat(cookies(false).clearRefreshToken().isSecure()).isFalse();
    }

    @Test
    @DisplayName("Secure 를 꺼도 HttpOnly·SameSite=Lax 는 그대로다")
    void othersKept() {
        var cookie = cookies(false).accessToken("t");

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    private static AuthCookies cookies(Boolean secure) {
        var jwt = new SecurityProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(14));
        return new AuthCookies(new SecurityProperties(jwt, Map.of(), null, secure));
    }
}
