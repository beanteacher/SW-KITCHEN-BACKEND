package com.swkitchen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.identity.IdentityVerifier;
import com.swkitchen.common.oauth.KakaoLoginClient;
import com.swkitchen.common.oauth.NaverLoginClient;
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
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class SocialLoginControllerTest {

    static final String AGREE = "{\"agreements\":{\"terms\":true,\"privacy\":true,\"marketing\":false}}";

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

    @MockitoBean
    IdentityVerifier identityVerifier;

    @MockitoBean
    KakaoLoginClient kakaoLoginClient;

    @MockitoBean
    NaverLoginClient naverLoginClient;

    Account kim;

    @BeforeEach
    void setUp() {
        given(kakaoLoginClient.authorizeUrl(anyString(), anyString()))
            .willAnswer(invocation -> "https://kauth.kakao.com/oauth/authorize?state=" + invocation.getArgument(0));
        kim = accountRepository.save(Account.create("kitchen01", passwordEncoder.encode("abcd123!"), "김주방", Role.CUSTOMER));
        accountIdentityRepository.save(AccountIdentity.create(
            kim, CryptoUtil.sha256("ci-kim"), LocalDate.of(1990, 5, 12), Gender.M, "01012345678", false, LocalDateTime.now()));
    }

    @Test
    @DisplayName("시작: 카카오 로그인 화면으로 보내고 oauth_state 쿠키를 둔다. 없는 업체는 404")
    void start() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth/kakao"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith("https://kauth.kakao.com/")))
            .andExpect(cookie().httpOnly("oauth_state", true))
            .andExpect(cookie().path("oauth_state", "/api/v1/auth/oauth"));
        mvc.perform(get("/api/v1/auth/oauth/google")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("콜백: state 가 다르면 로그인 화면에 오류로 보내고 로그인시키지 않는다")
    void wrongState() throws Exception {
        Started started = start("/");

        mvc.perform(get("/api/v1/auth/oauth/kakao/callback").param("code", "c").param("state", "other").cookie(started.cookie()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/login?error=social"))
            .andExpect(cookie().doesNotExist("access_token"));
    }

    @Test
    @DisplayName("처음 온 카카오 계정: 가입 화면으로 보내고, 본인확인·동의를 마치면 아이디 없는 계정으로 가입·로그인된다")
    void socialSignup() throws Exception {
        Cookie social = callback("kakao-new", "/").andExpect(header().string("Location", "/signup/social"))
            .andReturn().getResponse().getCookie("social_signup");
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-lee", "SIGNUP");

        mvc.perform(post("/api/v1/member/social").cookie(social, verified).contentType(MediaType.APPLICATION_JSON).content(AGREE))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.name").value("김주방"))
            .andExpect(cookie().exists("access_token"));

        Account account = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-new")
            .orElseThrow().getAccount();
        assertThat(account.getUserId()).isNull();
        callback("kakao-new", "/mypage")
            .andExpect(header().string("Location", "/mypage"))
            .andExpect(cookie().exists("access_token"));
    }

    @Test
    @DisplayName("본인확인을 마치기 전에 멈추면 계정이 생기지 않고, 소셜 쿠키가 없으면 400 SOCIAL_SIGNUP_EXPIRED")
    void socialSignupWithoutSteps() throws Exception {
        Cookie social = callback("kakao-new", "/").andReturn().getResponse().getCookie("social_signup");

        mvc.perform(post("/api/v1/member/social").cookie(social).contentType(MediaType.APPLICATION_JSON).content(AGREE))
            .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_REQUIRED"));
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-lee", "SIGNUP");
        mvc.perform(post("/api/v1/member/social").cookie(verified).contentType(MediaType.APPLICATION_JSON).content(AGREE))
            .andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_EXPIRED"));

        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-new")).isEmpty();
    }

    @Test
    @DisplayName("기존 아이디 가입자: 비밀번호가 틀리면 연결되지 않고, 맞으면 연결되어 그 뒤 카카오로 그 계정에 들어간다")
    void linkExisting() throws Exception {
        Cookie social = callback("kakao-kim", "/").andReturn().getResponse().getCookie("social_signup");
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");

        link(social, verified, "wrong-pass")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        link(social, verified, "abcd123!")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("kitchen01"));

        callback("kakao-kim", "/").andExpect(cookie().exists("access_token"));
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-kim")
            .orElseThrow().getAccount().getId()).isEqualTo(kim.getId());
    }

    @Test
    @DisplayName("이미 다른 카카오 계정이 연결된 계정에 또 연결하면 409 SOCIAL_ALREADY_LINKED")
    void alreadyLinked() throws Exception {
        socialAccountRepository.save(SocialAccount.create(kim, SocialProvider.KAKAO, "kakao-old"));
        Cookie social = callback("kakao-kim", "/").andReturn().getResponse().getCookie("social_signup");
        Cookie verified = IdentityCookies.verify(mvc, identityVerifier, "ci-kim", "SIGNUP");

        link(social, verified, "abcd123!")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SOCIAL_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("탈퇴 전 다시 확인: 로그인한 계정에 연결된 카카오면 social_reauth 쿠키를 주고, 그 쿠키로 탈퇴된다")
    void reauthWithdraw() throws Exception {
        Account lee = accountRepository.save(Account.create(null, null, "이식당", Role.CUSTOMER));
        accountIdentityRepository.save(AccountIdentity.create(
            lee, CryptoUtil.sha256("ci-lee"), LocalDate.of(1990, 5, 12), Gender.F, "01033334444", false, LocalDateTime.now()));
        socialAccountRepository.save(SocialAccount.create(lee, SocialProvider.KAKAO, "kakao-lee"));
        Cookie access = new Cookie("access_token", jwtProvider.createAccessToken(lee.getId(), Role.CUSTOMER));

        Started started = mvcStart("/mypage/withdraw", true, access);
        given(kakaoLoginClient.fetchUserId(eq("code"), eq(started.state()), anyString())).willReturn(Optional.of("kakao-other"));
        mvc.perform(get("/api/v1/auth/oauth/kakao/callback").param("code", "code").param("state", started.state())
                .cookie(started.cookie(), access))
            .andExpect(header().string("Location", "/mypage/withdraw?error=reauth"));

        Started again = mvcStart("/mypage/withdraw", true, access);
        given(kakaoLoginClient.fetchUserId(eq("code"), eq(again.state()), anyString())).willReturn(Optional.of("kakao-lee"));
        Cookie reauth = mvc.perform(get("/api/v1/auth/oauth/kakao/callback").param("code", "code").param("state", again.state())
                .cookie(again.cookie(), access))
            .andExpect(header().string("Location", "/mypage/withdraw"))
            .andReturn().getResponse().getCookie("social_reauth");

        mvc.perform(post("/api/v1/member/me/withdraw").cookie(access, reauth).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
        assertThat(accountRepository.findById(lee.getId())).isEmpty();
    }

    private ResultActions callback(String providerUserId, String returnUrl) throws Exception {
        Started started = start(returnUrl);
        given(kakaoLoginClient.fetchUserId(eq("code"), eq(started.state()), anyString())).willReturn(Optional.of(providerUserId));
        return mvc.perform(get("/api/v1/auth/oauth/kakao/callback")
            .param("code", "code")
            .param("state", started.state())
            .cookie(started.cookie()));
    }

    private ResultActions link(Cookie social, Cookie verified, String password) throws Exception {
        return mvc.perform(post("/api/v1/member/social/link")
            .cookie(social, verified)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"" + password + "\"}"));
    }

    private Started start(String returnUrl) throws Exception {
        return mvcStart(returnUrl, false);
    }

    private Started mvcStart(String returnUrl, boolean reauth, Cookie... cookies) throws Exception {
        var request = get("/api/v1/auth/oauth/kakao").param("returnUrl", returnUrl);
        if (reauth) {
            request.param("purpose", "reauth");
        }
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult result = mvc.perform(request).andReturn();
        String state = UriComponentsBuilder.fromUriString(result.getResponse().getHeader("Location"))
            .build().getQueryParams().getFirst("state");
        return new Started(state, result.getResponse().getCookie("oauth_state"));
    }

    record Started(String state, Cookie cookie) {
    }
}
