package com.swkitchen.auth.repository;

import static com.swkitchen.auth.domain.QRefreshToken.refreshToken;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public long deleteUnusable(LocalDateTime now, LocalDateTime rotatedBefore) {
        return queryFactory
            .delete(refreshToken)
            .where(refreshToken.expiresAt.loe(now).or(refreshToken.rotatedAt.loe(rotatedBefore)))
            .execute();
    }
}
