package com.swkitchen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.identity.IdentityVerifier;
import com.swkitchen.common.security.CryptoUtil;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.Gender;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.AccountRepository;
import com.swkitchen.member.repository.SocialAccountRepository;
import com.swkitchen.member.support.IdentityCookies;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MyPageControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountIdentityRepository accountIdentityRepository;

    @Autowired
    SocialAccountRepository socialAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    EntityManager em;

    @MockitoBean
    IdentityVerifier identityVerifier;

    Account kim;
    Account lee;

    @BeforeEach
    void setUp() {
        kim = accountRepository.save(Account.create("kitchen01", passwordEncoder.encode("abcd123!"), "김주방", Role.CUSTOMER));
        accountIdentityRepository.save(identity(kim, "ci-kim", "01011112222"));
        socialAccountRepository.save(SocialAccount.create(kim, SocialProvider.KAKAO, "3021457788"));
        lee = accountRepository.save(Account.create(null, null, "이식당", Role.CUSTOMER));
        accountIdentityRepository.save(identity(lee, "ci-lee", "01033334444"));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("내 정보: 생년월일·휴대폰 번호를 풀어서 주고, 아이디 가입이면 비밀번호가 있다")
    void me() throws Exception {
        mvc.perform(get("/api/v1/member/me").cookie(accessToken(kim)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("kitchen01"))
            .andExpect(jsonPath("$.data.name").value("김주방"))
            .andExpect(jsonPath("$.data.birthDate").value("1990-05-12"))
            .andExpect(jsonPath("$.data.phone").value("01011112222"))
            .andExpect(jsonPath("$.data.linkedProviders[0]").value("KAKAO"))
            .andExpect(jsonPath("$.data.marketingAgreed").value(true))
            .andExpect(jsonPath("$.data.hasPassword").value(true));
        mvc.perform(get("/api/v1/member/me").cookie(accessToken(lee)))
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.hasPassword").value(false));
    }

    @Test
    @DisplayName("직원 계정과 로그인 안 한 요청은 마이페이지를 쓸 수 없다")
    void forbidden() throws Exception {
        Account staff = accountRepository.save(Account.create("staff1", "hash", "박담당", Role.STAFF));

        mvc.perform(get("/api/v1/member/me").cookie(accessToken(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/member/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("마케팅 수신 동의를 끄면 저장되고 바꾼 시각이 바뀐다")
    void marketing() throws Exception {
        LocalDateTime before = accountIdentityRepository.findById(kim.getId()).orElseThrow().getMarketingChangedAt();

        mvc.perform(patch("/api/v1/member/me/marketing")
                .cookie(accessToken(kim))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreed\":false}"))
            .andExpect(status().isOk());
        em.flush();
        em.clear();

        AccountIdentity identity = accountIdentityRepository.findById(kim.getId()).orElseThrow();
        assertThat(identity.isMarketingAgreed()).isFalse();
        assertThat(identity.getMarketingChangedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("휴대폰 번호: 같은 사람의 본인확인이면 새 번호로 바뀌고, 다른 사람이면 409 IDENTITY_MISMATCH")
    void phone() throws Exception {
        Cookie other = IdentityCookies.verify(mvc, identityVerifier, "ci-lee", "PHONE_CHANGE");
        mvc.perform(patch("/api/v1/member/me/phone").cookie(accessToken(kim), other))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_MISMATCH"));

        Cookie same = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "PHONE_CHANGE");
        mvc.perform(patch("/api/v1/member/me/phone").cookie(accessToken(kim), same))
            .andExpect(status().isOk())
            .andExpect(cookie().maxAge("verified_identity", 0));
        em.flush();
        em.clear();

        assertThat(accountIdentityRepository.findById(kim.getId()).orElseThrow().getPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("비밀번호 변경: 고객은 특수문자 없는 새 비밀번호를 받지 않고, 소셜로만 가입한 계정은 409 NO_PASSWORD_ACCOUNT")
    void changePassword() throws Exception {
        changePassword(kim, "abcd123!", "abcd1234")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        changePassword(kim, "abcd123!", "newpass1!").andExpect(status().isOk());
        changePassword(lee, "whatever", "newpass1!")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NO_PASSWORD_ACCOUNT"));
    }

    @Test
    @DisplayName("탈퇴: 비밀번호가 틀리면 400 PASSWORD_MISMATCH, 맞으면 계정·본인확인 정보·소셜 연결을 지우고 쿠키를 지운다")
    void withdraw() throws Exception {
        withdraw(kim, "wrong-pass")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        withdraw(kim, "abcd123!")
            .andExpect(status().isOk())
            .andExpect(cookie().maxAge("access_token", 0))
            .andExpect(cookie().maxAge("refresh_token", 0));
        em.flush();
        em.clear();

        assertThat(accountRepository.findById(kim.getId())).isEmpty();
        assertThat(accountIdentityRepository.findById(kim.getId())).isEmpty();
        assertThat(socialAccountRepository.findAll()).noneMatch(s -> s.getProviderUserId().equals("3021457788"));
    }

    @Test
    @DisplayName("탈퇴한 사람은 바로 다시 가입할 수 있고, 새 계정에는 옛 소셜 연결이 없다")
    void signupAgain() throws Exception {
        withdraw(kim, "abcd123!").andExpect(status().isOk());
        em.flush();

        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");
        Cookie access = mvc.perform(post("/api/v1/member")
                .cookie(verified)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"kitchen01\",\"password\":\"abcd123!\",\"agreements\":{\"terms\":true,\"privacy\":true,\"marketing\":false}}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getCookie("access_token");

        mvc.perform(get("/api/v1/member/me").cookie(access))
            .andExpect(jsonPath("$.data.linkedProviders").isEmpty())
            .andExpect(jsonPath("$.data.marketingAgreed").value(false));
    }

    @Test
    @DisplayName("탈퇴: 소셜로만 가입한 계정은 소셜 로그인을 다시 하지 않으면 400 REAUTH_REQUIRED")
    void withdrawSocialOnly() throws Exception {
        withdraw(lee, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
    }

    private ResultActions changePassword(Account account, String current, String next) throws Exception {
        return mvc.perform(patch("/api/v1/auth/password")
            .cookie(accessToken(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions withdraw(Account account, String password) throws Exception {
        String body = password == null ? "{}" : "{\"password\":\"" + password + "\"}";
        return mvc.perform(post("/api/v1/member/me/withdraw")
            .cookie(accessToken(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private Cookie accessToken(Account account) {
        return new Cookie("access_token", jwtProvider.createAccessToken(account.getId(), account.getRole()));
    }

    private static AccountIdentity identity(Account account, String ci, String phone) {
        return AccountIdentity.create(
            account, CryptoUtil.sha256(ci), LocalDate.of(1990, 5, 12), Gender.M, phone, true, LocalDateTime.now());
    }
}
