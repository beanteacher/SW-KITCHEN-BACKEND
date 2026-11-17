package com.swkitchen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.identity.IdentityVerifier;
import com.swkitchen.common.identity.VerifiedPerson;
import com.swkitchen.common.security.CryptoUtil;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.Gender;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.AccountRepository;
import com.swkitchen.member.repository.SocialAccountRepository;
import com.swkitchen.member.support.MemberFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class IdentityVerificationControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountIdentityRepository accountIdentityRepository;

    @Autowired
    SocialAccountRepository socialAccountRepository;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    IdentityVerifier identityVerifier;

    @Test
    @DisplayName("성공: 이름·생년월일·휴대폰 번호를 주고 본인확인 쿠키(HttpOnly, /api/v1)를 준다")
    void verify() throws Exception {
        given(identityVerifier.fetch("iv-1")).willReturn(Optional.of(MemberFixtures.person("iv-1", "ci-kim")));

        verify("iv-1", "SIGNUP")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("김주방"))
            .andExpect(jsonPath("$.data.birthDate").value("1990-05-12"))
            .andExpect(jsonPath("$.data.phone").value("01012345678"))
            .andExpect(jsonPath("$.data.existingAccount").doesNotExist())
            .andExpect(jsonPath("$.data.ci").doesNotExist())
            .andExpect(cookie().httpOnly("verified_identity", true))
            .andExpect(cookie().path("verified_identity", "/api/v1"));
    }

    @Test
    @DisplayName("같은 사람의 계정이 있으면 아이디 앞 3자만 보이게 알려준다")
    void existingAccount() throws Exception {
        Account account = accountRepository.save(Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER));
        accountIdentityRepository.save(AccountIdentity.create(
            account, CryptoUtil.sha256("ci-kim"), LocalDate.of(1990, 5, 12), Gender.M, "01012345678", false, LocalDateTime.now()));
        socialAccountRepository.save(SocialAccount.create(account, SocialProvider.KAKAO, "3021457788"));
        given(identityVerifier.fetch("iv-1")).willReturn(Optional.of(MemberFixtures.person("iv-1", "ci-kim")));

        verify("iv-1", "SIGNUP")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.existingAccount.maskedUserId").value("kit******"))
            .andExpect(jsonPath("$.data.existingAccount.linkedProviders[0]").value("KAKAO"));
    }

    @Test
    @DisplayName("한 번 쓴 본인확인 번호는 다시 받지 않는다")
    void reuse() throws Exception {
        given(identityVerifier.fetch("iv-1")).willReturn(Optional.of(MemberFixtures.person("iv-1", "ci-kim")));

        verify("iv-1", "FIND").andExpect(status().isOk());
        verify("iv-1", "FIND")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("업체 결과가 없거나 인증한 지 10분이 지났으면 400 IDENTITY_VERIFICATION_FAILED")
    void failed() throws Exception {
        given(identityVerifier.fetch("iv-none")).willReturn(Optional.empty());
        VerifiedPerson old = MemberFixtures.person("iv-old", "ci-kim");
        given(identityVerifier.fetch("iv-old")).willReturn(Optional.of(new VerifiedPerson(
            old.verificationId(), old.ci(), old.name(), old.birthDate(), old.gender(), old.phone(),
            Instant.now().minusSeconds(10 * 60))));

        verify("iv-none", "SIGNUP").andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
        verify("iv-old", "SIGNUP").andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("가입 목적이면 오늘 만 14세가 된 사람은 받고, 하루 모자라면 400 UNDER_AGE. 찾기 목적은 나이를 보지 않는다")
    void underAge() throws Exception {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        given(identityVerifier.fetch("iv-14")).willReturn(Optional.of(
            MemberFixtures.person("iv-14", "ci-a", today.minusYears(14))));
        given(identityVerifier.fetch("iv-13")).willReturn(Optional.of(
            MemberFixtures.person("iv-13", "ci-b", today.minusYears(14).plusDays(1))));
        given(identityVerifier.fetch("iv-13-find")).willReturn(Optional.of(
            MemberFixtures.person("iv-13-find", "ci-b", today.minusYears(14).plusDays(1))));

        verify("iv-14", "SIGNUP").andExpect(status().isOk());
        verify("iv-13", "SIGNUP")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNDER_AGE"));
        verify("iv-13-find", "FIND").andExpect(status().isOk());
    }

    @Test
    @DisplayName("생년월일·휴대폰 번호는 DB 에 암호문으로만 있고, CI 는 해시로만 있다")
    void encryptedInDatabase() {
        Account account = accountRepository.save(Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER));
        accountIdentityRepository.saveAndFlush(AccountIdentity.create(
            account, CryptoUtil.sha256("ci-kim"), LocalDate.of(1990, 5, 12), Gender.M, "01012345678", false, LocalDateTime.now()));

        var row = jdbc.queryForMap("SELECT ci_hash, birth_date_enc, phone_enc FROM account_identity WHERE account_id = ?",
            account.getId());
        assertThat((String) row.get("birth_date_enc")).doesNotContain("1990");
        assertThat((String) row.get("phone_enc")).doesNotContain("0101234");
        assertThat(row.get("ci_hash")).isEqualTo(CryptoUtil.sha256("ci-kim"));
        assertThat(accountIdentityRepository.findById(account.getId()).orElseThrow().getPhone()).isEqualTo("01012345678");
    }

    private ResultActions verify(String id, String purpose) throws Exception {
        return mvc.perform(post("/api/v1/identity-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"identityVerificationId\":\"" + id + "\",\"purpose\":\"" + purpose + "\"}"));
    }
}
