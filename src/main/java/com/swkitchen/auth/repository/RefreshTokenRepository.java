package com.swkitchen.auth.repository;

import com.swkitchen.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByTokenHashAndAccountId(String tokenHash, Long accountId);

    void deleteByAccountIdAndTokenHashNot(Long accountId, String tokenHash);

    void deleteByAccountId(Long accountId);
}
