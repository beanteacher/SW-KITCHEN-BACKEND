package com.swkitchen.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private static final Instant START = Instant.parse("2026-11-07T01:00:00Z");

    LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    @DisplayName("1분에 20번까지는 받고, 21번째부터 막는다")
    void blockAfterTwentyAttempts() {
        for (int i = 0; i < 20; i++) {
            limiter.check("1.1.1.1", START.plusSeconds(i));
        }

        assertBlocked("1.1.1.1", START.plusSeconds(20));
    }

    @Test
    @DisplayName("다른 IP 는 막히지 않는다")
    void otherIp() {
        for (int i = 0; i < 21; i++) {
            check("1.1.1.1", START.plusSeconds(i));
        }

        assertThatCode(() -> limiter.check("2.2.2.2", START.plusSeconds(21))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1분이 지난 시도는 세지 않는다")
    void oldAttemptsExpire() {
        for (int i = 0; i < 20; i++) {
            limiter.check("1.1.1.1", START);
        }

        assertThatCode(() -> limiter.check("1.1.1.1", START.plusSeconds(60))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("막히면 10분 동안 막히고, 10분 뒤에는 다시 받는다")
    void blockForTenMinutes() {
        for (int i = 0; i < 21; i++) {
            check("1.1.1.1", START);
        }

        assertBlocked("1.1.1.1", START.plusSeconds(10 * 60 - 1));
        assertThatCode(() -> limiter.check("1.1.1.1", START.plusSeconds(10 * 60))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정리: 1분 동안 시도가 없는 IP 는 지우고, 막힌 IP 는 남긴다")
    void removeIdle() {
        limiter.check("1.1.1.1", START);
        for (int i = 0; i < 21; i++) {
            check("2.2.2.2", START);
        }

        limiter.removeIdle(START.plusSeconds(61));

        assertThat(limiter.trackedIpCount()).isEqualTo(1);
        assertBlocked("2.2.2.2", START.plusSeconds(61));
    }

    private void check(String ip, Instant now) {
        try {
            limiter.check(ip, now);
        } catch (AppException ignored) {
            // 막히는 것까지 채우려고 부른다
        }
    }

    private void assertBlocked(String ip, Instant now) {
        assertThatThrownBy(() -> limiter.check(ip, now))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.LOGIN_RATE_LIMITED);
    }
}
