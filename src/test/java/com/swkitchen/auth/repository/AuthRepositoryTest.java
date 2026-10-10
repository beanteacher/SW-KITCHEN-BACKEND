package com.swkitchen.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.RefreshToken;
import com.swkitchen.auth.domain.Role;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
        accountRepository.saveAndFlush(Account.create("staff1", "hash", Role.STAFF));

        assertThat(accountRepository.findByUserId("staff1"))
            .get().extracting(Account::getRole).isEqualTo(Role.STAFF);
    }

    @Test
    @DisplayName("같은 아이디는 두 번 만들 수 없다")
    void duplicateUserId() {
        accountRepository.saveAndFlush(Account.create("kimchef", "hash", Role.CUSTOMER));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(Account.create("kimchef", "hash", Role.CUSTOMER)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정해진 역할 외의 값은 DB 가 막는다")
    void roleCheck() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO account (user_id, password_hash, role, created_at, updated_at) VALUES ('owner1', 'h', 'OWNER', NOW(6), NOW(6))"))
            .hasMessageContaining("ck_account_role");
    }

    @Test
    @DisplayName("리프레시 토큰을 해시로 찾는다")
    void findByTokenHash() {
        Account account = accountRepository.saveAndFlush(Account.create("parkcook", "hash", Role.CUSTOMER));
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
}
