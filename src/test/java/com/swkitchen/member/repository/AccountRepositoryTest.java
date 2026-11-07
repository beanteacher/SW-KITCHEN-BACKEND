package com.swkitchen.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@RepositoryTest
class AccountRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    SocialAccountRepository socialAccountRepository;

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
    @DisplayName("고객 찾기: 고객만 최신 가입순으로 주고, 아이디나 이름 일부로 찾는다")
    void searchCustomers() {
        Account kim = accountRepository.save(Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER));
        Account lee = accountRepository.save(Account.create(null, null, "이식당", Role.CUSTOMER));
        accountRepository.save(Account.create("chef02", "hash", "김담당", Role.STAFF));
        accountRepository.flush();
        PageRequest page = PageRequest.of(0, 20);

        assertThat(accountRepository.searchCustomers(null, page).getContent())
            .extracting(Account::getId).containsExactly(lee.getId(), kim.getId());
        assertThat(accountRepository.searchCustomers("김", page).getContent())
            .extracting(Account::getId).containsExactly(kim.getId());
        assertThat(accountRepository.searchCustomers("chen0", page).getContent())
            .extracting(Account::getId).containsExactly(kim.getId());
    }

    @Test
    @DisplayName("고객 찾기: 검색어의 % 와 _ 는 글자 그대로 찾는다")
    void searchCustomersEscapesWildcards() {
        accountRepository.saveAndFlush(Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER));

        assertThat(accountRepository.searchCustomers("%", PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(accountRepository.searchCustomers("_", PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("소셜 연결: 계정에서 연결된 소셜을 읽고, 한 계정에 같은 소셜을 두 번 연결할 수 없다")
    void socialAccount() {
        Account account = accountRepository.saveAndFlush(Account.create(null, null, "이식당", Role.CUSTOMER));
        socialAccountRepository.saveAndFlush(SocialAccount.create(account, SocialProvider.KAKAO, "3019920451"));
        em.clear();

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getSocialAccounts())
            .extracting(SocialAccount::getProvider).containsExactly(SocialProvider.KAKAO);
        assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
            SocialAccount.create(em.getReference(Account.class, account.getId()), SocialProvider.KAKAO, "999")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
