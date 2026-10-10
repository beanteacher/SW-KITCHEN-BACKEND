package com.swkitchen.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.auth.repository.AccountRepository;
import com.swkitchen.auth.repository.RefreshTokenRepository;
import com.swkitchen.common.security.SecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SecurityProperties properties;

    @BeforeEach
    void setUp() {
        accountRepository.save(Account.create("staff1", passwordEncoder.encode("pass1234"), Role.STAFF));
    }

    @Test
    @DisplayName("로그인 성공: 계정 정보와 두 토큰 쿠키(HttpOnly·Secure·SameSite=Lax)를 주고 리프레시 토큰은 해시로 저장한다")
    void loginSuccess() throws Exception {
        MvcResult result = login("staff1", "pass1234")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value("staff1"))
            .andExpect(jsonPath("$.data.role").value("STAFF"))
            .andExpect(jsonPath("$.data.passwordChangeRecommended").value(false))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(cookie().httpOnly("access_token", true))
            .andExpect(cookie().secure("access_token", true))
            .andExpect(cookie().sameSite("access_token", "Lax"))
            .andExpect(cookie().path("access_token", "/"))
            .andExpect(cookie().maxAge("access_token", 30 * 60))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
            .andExpect(cookie().maxAge("refresh_token", 14 * 24 * 60 * 60))
            .andReturn();

        String refresh = result.getResponse().getCookie("refresh_token").getValue();
        assertThat(refreshTokenRepository.findByTokenHash(sha256(refresh))).isPresent();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 LOGIN_FAILED, 쿠키 없음")
    void wrongPassword() throws Exception {
        login("staff1", "wrong-pass")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 틀림과 같은 응답이다 (아이디 존재를 알리지 않음)")
    void unknownUserId() throws Exception {
        login("nobody", "pass1234")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
            .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 맞지 않습니다."));
    }

    @Test
    @DisplayName("72바이트를 넘는 비밀번호는 500 이 아니라 LOGIN_FAILED")
    void tooLongPassword() throws Exception {
        login("staff1", "가".repeat(30))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("아이디를 비우면 400 VALIDATION_ERROR, errors 에 userId")
    void blankUserId() throws Exception {
        login("", "pass1234")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    @Test
    @DisplayName("로그아웃: 이 리프레시 토큰 행을 지우고 두 쿠키를 지운다")
    void logout() throws Exception {
        MvcResult loggedIn = login("staff1", "pass1234").andReturn();
        Cookie access = loggedIn.getResponse().getCookie("access_token");
        Cookie refresh = loggedIn.getResponse().getCookie("refresh_token");

        mvc.perform(post("/api/v1/auth/logout").cookie(access, refresh))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(cookie().maxAge("access_token", 0))
            .andExpect(cookie().maxAge("refresh_token", 0));

        assertThat(refreshTokenRepository.findByTokenHash(sha256(refresh.getValue()))).isEmpty();
    }

    @Test
    @DisplayName("로그아웃은 다른 계정의 리프레시 토큰을 지우지 못한다")
    void logoutCannotDeleteOthersToken() throws Exception {
        accountRepository.save(Account.create("staff2", passwordEncoder.encode("pass1234"), Role.STAFF));
        Cookie othersRefresh = login("staff2", "pass1234").andReturn().getResponse().getCookie("refresh_token");
        Cookie myAccess = login("staff1", "pass1234").andReturn().getResponse().getCookie("access_token");

        mvc.perform(post("/api/v1/auth/logout").cookie(myAccess, othersRefresh))
            .andExpect(status().isOk());

        assertThat(refreshTokenRepository.findByTokenHash(sha256(othersRefresh.getValue()))).isPresent();
    }

    @Test
    @DisplayName("로그인 안 하고 로그아웃하면 401 UNAUTHORIZED")
    void logoutWithoutLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 액세스 토큰은 401")
    void expiredToken() throws Exception {
        String expired = Jwts.builder()
            .subject("1").claim("role", "STAFF")
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8)))
            .compact();

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("access_token", expired)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 키로 서명한(위조) 액세스 토큰은 401")
    void forgedToken() throws Exception {
        String forged = Jwts.builder()
            .subject("1").claim("role", "ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-0123456789".getBytes(StandardCharsets.UTF_8)))
            .compact();

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("access_token", forged)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공개 경로(제품 조회)는 로그인 없이 들어간다")
    void publicPath() throws Exception {
        // 아직 제품 API 가 없어 404 지만, 401 이 아니면 보안 단계는 통과한 것이다
        mvc.perform(get("/api/v1/products"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 경로가 아닌 곳은 로그인 없이 401")
    void protectedPath() throws Exception {
        mvc.perform(get("/api/v1/admin/products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("설정의 첫 관리자 계정이 만들어지고, 첫 비밀번호로 로그인하면 변경 안내 값이 true")
    void initialAdmin() throws Exception {
        login("admin", "1234")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.passwordChangeRecommended").value(true));
    }

    @Test
    @DisplayName("비밀번호 변경: 새 비밀번호로만 로그인되고, 이 기기는 남고 다른 기기는 로그아웃된다")
    void changePassword() throws Exception {
        Cookie otherRefresh = login("staff1", "pass1234").andReturn().getResponse().getCookie("refresh_token");
        MvcResult me = login("staff1", "pass1234").andReturn();
        Cookie access = me.getResponse().getCookie("access_token");
        Cookie refresh = me.getResponse().getCookie("refresh_token");

        changePassword("pass1234", "newpass", access, refresh)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(refreshTokenRepository.findByTokenHash(sha256(refresh.getValue()))).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(sha256(otherRefresh.getValue()))).isEmpty();
        login("staff1", "pass1234").andExpect(status().isUnauthorized());
        login("staff1", "newpass").andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 PASSWORD_MISMATCH, 비밀번호는 그대로")
    void changePasswordWrongCurrent() throws Exception {
        Cookie access = login("staff1", "pass1234").andReturn().getResponse().getCookie("access_token");

        changePassword("wrong-pass", "newpass", access)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"))
            .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
            .andExpect(jsonPath("$.errors[0].message").value("현재 비밀번호가 맞지 않습니다."));

        login("staff1", "pass1234").andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 비밀번호가 비었거나 72바이트를 넘으면 400 VALIDATION_ERROR")
    void changePasswordInvalidNew() throws Exception {
        Cookie access = login("staff1", "pass1234").andReturn().getResponse().getCookie("access_token");

        changePassword("pass1234", "", access)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        changePassword("pass1234", "가".repeat(30), access)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
            .andExpect(jsonPath("$.errors[0].message").value("비밀번호가 너무 깁니다. (영문 72자, 한글 24자 이하)"));

        login("staff1", "pass1234").andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 안 하고 비밀번호를 바꾸면 401 UNAUTHORIZED")
    void changePasswordWithoutLogin() throws Exception {
        changePassword("pass1234", "newpass")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions changePassword(String current, String next, Cookie... cookies) throws Exception {
        var request = patch("/api/v1/auth/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request);
    }

    private ResultActions login(String userId, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
