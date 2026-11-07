package com.swkitchen.auth.repository;

import com.swkitchen.auth.domain.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByTokenHashAndAccountId(String tokenHash, Long accountId);

    void deleteByAccountIdAndTokenHashNot(Long accountId, String tokenHash);

    void deleteByAccountId(Long accountId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt <= :now OR t.rotatedAt <= :rotatedBefore")
    int deleteUnusable(LocalDateTime now, LocalDateTime rotatedBefore);
}
