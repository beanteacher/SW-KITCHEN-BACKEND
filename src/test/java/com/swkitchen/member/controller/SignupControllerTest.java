package com.swkitchen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.identity.IdentityVerifier;
import com.swkitchen.common.security.CryptoUtil;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.AccountRepository;
import com.swkitchen.member.support.IdentityCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class SignupControllerTest {

    static final String AGREE_ALL = "{\"terms\":true,\"privacy\":true,\"marketing\":true}";
    static final String AGREE_REQUIRED = "{\"terms\":true,\"privacy\":true,\"marketing\":false}";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountIdentityRepository accountIdentityRepository;

    @MockitoBean
    IdentityVerifier identityVerifier;

    @Test
    @DisplayName("가입: 201 이고 바로 로그인되며, 본인확인 결과·동의가 저장되고 본인확인 쿠키는 지운다")
    void signup() throws Exception {
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");

        signup(verified, "kitchen01", "abcd123!", AGREE_REQUIRED)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value("kitchen01"))
            .andExpect(jsonPath("$.data.name").value("김주방"))
            .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(cookie().maxAge("verified_identity", 0));

        Account account = accountRepository.findByUserId("kitchen01").orElseThrow();
        AccountIdentity identity = accountIdentityRepository.findById(account.getId()).orElseThrow();
        assertThat(identity.getCiHash()).isEqualTo(CryptoUtil.sha256("ci-kim"));
        assertThat(identity.getPhone()).isEqualTo("01012345678");
        assertThat(identity.isMarketingAgreed()).isFalse();
        assertThat(identity.getTermsAgreedAt()).isNotNull();
        assertThat(identity.getMarketingChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("가입한 고객 계정으로 백오피스 API 를 부르면 거부된다")
    void customerCannotUseAdmin() throws Exception {
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");
        Cookie access = signup(verified, "kitchen01", "abcd123!", AGREE_ALL)
            .andReturn().getResponse().getCookie("access_token");

        mvc.perform(get("/api/v1/admin/product").cookie(access)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("본인확인을 하지 않았거나 찾기용 본인확인이면 400 IDENTITY_VERIFICATION_REQUIRED, 계정이 생기지 않는다")
    void needsSignupVerification() throws Exception {
        signup(null, "kitchen01", "abcd123!", AGREE_ALL)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        Cookie find = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "FIND");
        signup(find, "kitchen01", "abcd123!", AGREE_ALL)
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        signup(new Cookie("verified_identity", "forged"), "kitchen01", "abcd123!", AGREE_ALL)
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));

        assertThat(accountRepository.findByUserId("kitchen01")).isEmpty();
    }

    @Test
    @DisplayName("아이디 대문자·3자, 특수문자 없는 비밀번호, 필수 동의 빠짐은 400")
    void invalidInput() throws Exception {
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");

        signup(verified, "Kitchen1", "abcd123!", AGREE_ALL).andExpect(jsonPath("$.errors[0].field").value("userId"));
        signup(verified, "abc", "abcd123!", AGREE_ALL).andExpect(jsonPath("$.errors[0].field").value("userId"));
        signup(verified, "kitchen01", "abcd1234", AGREE_ALL).andExpect(jsonPath("$.errors[0].field").value("password"));
        signup(verified, "kitchen01", "abcd123!", "{\"terms\":false,\"privacy\":true,\"marketing\":true}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("agreements.terms"));
    }

    @Test
    @DisplayName("이미 있는 아이디는 409 USER_ID_DUPLICATE, 같은 사람이 또 가입하면 409 ACCOUNT_ALREADY_EXISTS")
    void duplicates() throws Exception {
        accountRepository.save(Account.create("staff1", "hash", "박담당", Role.STAFF));
        Cookie first = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");
        signup(first, "staff1", "abcd123!", AGREE_ALL)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USER_ID_DUPLICATE"));
        signup(first, "kitchen01", "abcd123!", AGREE_ALL).andExpect(status().isCreated());

        Cookie again = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");
        signup(again, "kitchen02", "abcd123!", AGREE_ALL)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("아이디 중복 확인: 쓸 수 있으면 true, 이미 있으면 false, 형식이 틀리면 400")
    void checkUserId() throws Exception {
        accountRepository.save(Account.create("staff1", "hash", "박담당", Role.STAFF));

        mvc.perform(get("/api/v1/member/user-id-check").param("userId", "kitchen01"))
            .andExpect(jsonPath("$.data.available").value(true));
        mvc.perform(get("/api/v1/member/user-id-check").param("userId", "staff1"))
            .andExpect(jsonPath("$.data.available").value(false));
        mvc.perform(get("/api/v1/member/user-id-check").param("userId", "ab"))
            .andExpect(status().isBadRequest());
    }

    private ResultActions signup(Cookie verified, String userId, String password, String agreements) throws Exception {
        var request = post("/api/v1/member")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\",\"agreements\":" + agreements + "}");
        if (verified != null) {
            request.cookie(verified);
        }
        return mvc.perform(request);
    }
}
