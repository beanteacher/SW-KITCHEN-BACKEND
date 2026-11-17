package com.swkitchen.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.auth.domain.RefreshToken;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.repository.AccountRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class AuthRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("리프레시 토큰을 해시로 찾는다")
    void findByTokenHash() {
        Account account = accountRepository.saveAndFlush(Account.create("parkcook", "hash", "박요리", Role.CUSTOMER));
        String hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        refreshTokenRepository.saveAndFlush(RefreshToken.create(account, hash, LocalDateTime.now().plusDays(14)));

        assertThat(refreshTokenRepository.findByTokenHash(hash))
            .get().extracting(token -> token.getAccount().getId()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("없는 계정의 리프레시 토큰은 저장할 수 없다")
    void tokenNeedsAccount() {
        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(
            RefreshToken.create(em.getReference(Account.class, 999_999L), "a".repeat(64), LocalDateTime.now().plusDays(14))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정리: 만료된 토큰과 바뀐 뒤 30초가 지난 토큰만 지운다")
    void deleteUnusable() {
        Account account = accountRepository.saveAndFlush(Account.create("kang1", "hash", "강주방", Role.CUSTOMER));
        LocalDateTime now = LocalDateTime.now();
        RefreshToken expired = RefreshToken.create(account, "1".repeat(64), now.minusSeconds(1));
        RefreshToken rotatedLongAgo = RefreshToken.create(account, "2".repeat(64), now.plusDays(1));
        rotatedLongAgo.markRotated(now.minusSeconds(31));
        RefreshToken rotatedJustNow = RefreshToken.create(account, "3".repeat(64), now.plusDays(1));
        rotatedJustNow.markRotated(now.minusSeconds(10));
        RefreshToken active = RefreshToken.create(account, "4".repeat(64), now.plusDays(1));
        refreshTokenRepository.saveAllAndFlush(List.of(expired, rotatedLongAgo, rotatedJustNow, active));

        long deleted = refreshTokenRepository.deleteUnusable(now, now.minusSeconds(30));
        em.clear();

        assertThat(deleted).isEqualTo(2L);
        assertThat(refreshTokenRepository.findAll())
            .extracting(RefreshToken::getTokenHash)
            .containsExactlyInAnyOrder("3".repeat(64), "4".repeat(64));
    }
}
