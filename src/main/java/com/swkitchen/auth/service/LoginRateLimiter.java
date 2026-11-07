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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 서버가 한 대라 메모리에 둔다. 서버를 늘리면 공유 저장소로 옮긴다
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration BLOCK_TIME = Duration.ofMinutes(10);

    private final Map<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public void check(String ip) {
        check(ip, Instant.now());
    }

    void check(String ip, Instant now) {
        Attempts attempts = attemptsByIp.computeIfAbsent(ip, key -> new Attempts());
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

        private final Deque<Instant> times = new ArrayDeque<>();
        private Instant blockedUntil = Instant.MIN;

        synchronized boolean tryAcquire(Instant now) {
            if (now.isBefore(blockedUntil)) {
                return false;
            }
            dropOld(now);
            times.addLast(now);
            if (times.size() > MAX_ATTEMPTS) {
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
