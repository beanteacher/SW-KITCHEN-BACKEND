package com.swkitchen.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.identity.IdentityVerifier;
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
import com.swkitchen.member.support.IdentityCookies;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
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
class FindAccountControllerTest {

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

    @MockitoBean
    IdentityVerifier identityVerifier;

    // IP 시도 제한은 테스트끼리 나눠 쓰므로 테스트마다 다른 IP 로 보낸다
    String ip;

    @BeforeEach
    void setUp() {
        Account kim = accountRepository.save(Account.create("kitchen01", passwordEncoder.encode("abcd123!"), "김주방", Role.CUSTOMER));
        accountIdentityRepository.save(identity(kim, "ci-kim"));
        socialAccountRepository.save(SocialAccount.create(kim, SocialProvider.KAKAO, "3021457788"));
        Account lee = accountRepository.save(Account.create(null, null, "이식당", Role.CUSTOMER));
        accountIdentityRepository.save(identity(lee, "ci-lee"));
        socialAccountRepository.save(SocialAccount.create(lee, SocialProvider.NAVER, "naver-1"));
        ip = "test-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("아이디 찾기: 아이디 가입 후 카카오를 연결한 사람은 아이디 전체·가입일·카카오가 보인다")
    void findId() throws Exception {
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "FIND");

        mvc.perform(get("/api/v1/member/find-id").cookie(verified))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("kitchen01"))
            .andExpect(jsonPath("$.data.linkedProviders[0]").value("KAKAO"))
            .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("아이디 찾기: 소셜로만 가입했으면 아이디가 null, 계정이 없으면 404, 본인확인이 없으면 400")
    void findIdOthers() throws Exception {
        mvc.perform(get("/api/v1/member/find-id").cookie(IdentityCookies.verify(mvc, identityVerifier, "ci-lee", "FIND")))
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.linkedProviders[0]").value("NAVER"));
        mvc.perform(get("/api/v1/member/find-id").cookie(IdentityCookies.verify(mvc, identityVerifier, "ci-nobody", "FIND")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(get("/api/v1/member/find-id"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("재설정: 계정 주인이면 새 비밀번호로만 로그인되고 다른 기기는 로그아웃되며 본인확인 쿠키를 지운다")
    void resetPassword() throws Exception {
        Cookie otherDevice = login("kitchen01", "abcd123!").andReturn().getResponse().getCookie("refresh_token");
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "FIND");

        reset(verified, "kitchen01", "newpass1!")
            .andExpect(status().isOk())
            .andExpect(cookie().maxAge("verified_identity", 0));

        mvc.perform(post("/api/v1/auth/refresh").cookie(otherDevice)).andExpect(status().isUnauthorized());
        login("kitchen01", "abcd123!").andExpect(status().isUnauthorized());
        login("kitchen01", "newpass1!").andExpect(status().isOk());
    }

    @Test
    @DisplayName("재설정: 다른 사람이 자기 본인확인으로 그 아이디를 바꾸려 하면 409 IDENTITY_MISMATCH")
    void resetOthersPassword() throws Exception {
        Cookie lee = IdentityCookies.verify(mvc, identityVerifier, "ci-lee", "FIND");

        reset(lee, "kitchen01", "newpass1!")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_MISMATCH"));
        login("kitchen01", "abcd123!").andExpect(status().isOk());
    }

    @Test
    @DisplayName("재설정: 특수문자 없는 비밀번호는 400, 가입용 본인확인으로는 재설정할 수 없다")
    void resetInvalid() throws Exception {
        Cookie find = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "FIND");
        reset(find, "kitchen01", "abcd1234")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

        Cookie signup = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");
        reset(signup, "kitchen01", "newpass1!")
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
    }

    private ResultActions reset(Cookie verified, String userId, String newPassword) throws Exception {
        return mvc.perform(patch("/api/v1/member/password-reset")
            .cookie(verified)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String userId, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"));
    }

    private static AccountIdentity identity(Account account, String ci) {
        return AccountIdentity.create(
            account, CryptoUtil.sha256(ci), LocalDate.of(1990, 5, 12), Gender.M, "01012345678", false, LocalDateTime.now());
    }
}
