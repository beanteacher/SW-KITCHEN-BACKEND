package com.swkitchen.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.RefreshToken;
import com.swkitchen.auth.domain.Role;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@RepositoryTest
class AuthRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("아이디로 계정을 찾는다")
    void findByUserId() {
        accountRepository.saveAndFlush(Account.create("staff1", "hash", "박담당", Role.STAFF));

        assertThat(accountRepository.findByUserId("staff1"))
            .get().extracting(Account::getRole).isEqualTo(Role.STAFF);
    }

    @Test
    @DisplayName("같은 아이디는 두 번 만들 수 없다")
    void duplicateUserId() {
        accountRepository.saveAndFlush(Account.create("kimchef", "hash", "김주방", Role.CUSTOMER));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(Account.create("kimchef", "hash", "김주방", Role.CUSTOMER)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정해진 역할 외의 값은 DB 가 막는다")
    void roleCheck() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO account (user_id, password_hash, name, role, created_at, updated_at) VALUES ('owner1', 'h', '사장', 'OWNER', NOW(6), NOW(6))"))
            .hasMessageContaining("ck_account_role");
    }

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
    @DisplayName("소셜로만 가입한 계정은 아이디·비밀번호 없이 저장되고, 아이디가 없는 계정은 여럿이어도 된다")
    void accountWithoutLogin() {
        accountRepository.saveAndFlush(Account.create(null, null, "이식당", Role.CUSTOMER));
        accountRepository.saveAndFlush(Account.create(null, null, "최식당", Role.CUSTOMER));

        assertThat(accountRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("아이디와 비밀번호는 함께 있거나 함께 없어야 한다")
    void loginCheck() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO account (user_id, password_hash, name, role, created_at, updated_at) VALUES ('kim1', NULL, '김', 'CUSTOMER', NOW(6), NOW(6))"))
            .hasMessageContaining("ck_account_login");
    }

    @Test
    @DisplayName("새 계정은 사용 중이고 틀린 횟수가 0 이다")
    void defaultStatus() {
        Account account = accountRepository.saveAndFlush(Account.create("staff2", "hash", "이담당", Role.STAFF));
        em.clear();

        Account found = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(found.isSuspended()).isFalse();
        assertThat(found.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("틀린 횟수는 DB 에서 하나씩 더한다")
    void increaseLoginFailCount() {
        Account account = accountRepository.saveAndFlush(Account.create("staff3", "hash", "최담당", Role.STAFF));

        accountRepository.increaseLoginFailCount(account.getId());
        accountRepository.increaseLoginFailCount(account.getId());
        em.clear();

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getLoginFailCount()).isEqualTo(2);
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

        int deleted = refreshTokenRepository.deleteUnusable(now, now.minusSeconds(30));
        em.clear();

        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.findAll())
            .extracting(RefreshToken::getTokenHash)
            .containsExactlyInAnyOrder("3".repeat(64), "4".repeat(64));
    }
}
