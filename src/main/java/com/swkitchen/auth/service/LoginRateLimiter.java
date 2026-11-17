package com.swkitchen.auth.service;

import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 서버가 한 대라 메모리에 둔다. 서버를 늘리면 공유 저장소로 옮긴다
@Component
public class LoginRateLimiter {

    private static final int DEFAULT_MAX_ATTEMPTS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration BLOCK_TIME = Duration.ofMinutes(10);

    private final Map<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();
    // 1분에 받는 시도 수. 운영은 20 이고, 로컬은 E2E 가 한 컴퓨터에서 여러 번 로그인해 넉넉히 둔다
    private final int maxAttempts;

    public LoginRateLimiter(@Value("${security.login-attempts-per-minute:" + DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void check(String ip) {
        check(ip, Instant.now());
    }

    void check(String ip, Instant now) {
        Attempts attempts = attemptsByIp.computeIfAbsent(ip, key -> new Attempts(maxAttempts));
        if (!attempts.tryAcquire(now)) {
            throw new AppException(ErrorCode.LOGIN_RATE_LIMITED);
        }
    }

    // 지우지 않으면 한 번 온 IP 가 계속 쌓인다
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void removeIdle() {
        removeIdle(Instant.now());
    }

    void removeIdle(Instant now) {
        attemptsByIp.values().removeIf(attempts -> attempts.isIdle(now));
    }

    int trackedIpCount() {
        return attemptsByIp.size();
    }

    private static class Attempts {

        private final int maxAttempts;
        private final Deque<Instant> times = new ArrayDeque<>();
        private Instant blockedUntil = Instant.MIN;

        Attempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        synchronized boolean tryAcquire(Instant now) {
            if (now.isBefore(blockedUntil)) {
                return false;
            }
            dropOld(now);
            times.addLast(now);
            if (times.size() > maxAttempts) {
                blockedUntil = now.plus(BLOCK_TIME);
                times.clear();
                return false;
            }
            return true;
        }

        synchronized boolean isIdle(Instant now) {
            dropOld(now);
            return times.isEmpty() && !now.isBefore(blockedUntil);
        }

        private void dropOld(Instant now) {
            Instant from = now.minus(WINDOW);
            while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
                times.pollFirst();
            }
        }
    }
}
