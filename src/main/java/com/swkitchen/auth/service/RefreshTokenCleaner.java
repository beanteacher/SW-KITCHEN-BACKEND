package com.swkitchen.auth.service;

import com.swkitchen.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteUnusable() {
        LocalDateTime now = LocalDateTime.now();
        long deleted = refreshTokenRepository.deleteUnusable(now, now.minus(AuthService.ROTATED_TOKEN_GRACE_TIME));
        log.info("쓸 수 없는 리프레시 토큰 {}개를 지웠습니다", deleted);
    }
}
