package com.swkitchen.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.RefreshToken;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.repository.AccountRepository;
import com.swkitchen.auth.repository.RefreshTokenRepository;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.common.security.SecurityProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtProvider jwtProvider;

    AuthService authService;
    Account staff;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
            new SecurityProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(14)),
            Map.of(),
            new SecurityProperties.InitialAdmin("admin", "init-pass"));
        authService = new AuthService(accountRepository, refreshTokenRepository, passwordEncoder, jwtProvider, properties);

        staff = Account.create("staff1", "hash", Role.STAFF);
        ReflectionTestUtils.setField(staff, "id", 1L);
    }

    // ── 로그인 ──

    @Test
    @DisplayName("로그인 성공: 토큰을 주고 리프레시 토큰은 원문이 아니라 해시로 14일 뒤 만료로 저장한다")
    void login() {
        given(accountRepository.findByUserId("staff1")).willReturn(Optional.of(staff));
        given(passwordEncoder.matches("pass1234", "hash")).willReturn(true);
        given(jwtProvider.createAccessToken(1L, Role.STAFF)).willReturn("access");

        AuthDto.LoginResult result = authService.login(new AuthDto.LoginRequest("staff1", "pass1234"));

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.passwordChangeRecommended()).isFalse();
        assertThat(saved.getValue().getAccount().getId()).isEqualTo(1L);
        assertThat(saved.getValue().getTokenHash())
            .isEqualTo(AuthService.sha256(result.refreshToken()))
            .isNotEqualTo(result.refreshToken());
        assertThat(saved.getValue().getExpiresAt())
            .isBetween(LocalDateTime.now().plusDays(14).minusMinutes(1), LocalDateTime.now().plusDays(14));
    }

    @Test
    @DisplayName("로그인: 첫 관리자 비밀번호 그대로 들어오면 변경 안내 값이 true")
    void loginWithInitialPassword() {
        given(accountRepository.findByUserId("admin")).willReturn(Optional.of(staff));
        given(passwordEncoder.matches("init-pass", "hash")).willReturn(true);

        AuthDto.LoginResult result = authService.login(new AuthDto.LoginRequest("admin", "init-pass"));

        assertThat(result.passwordChangeRecommended()).isTrue();
    }

    @Test
    @DisplayName("로그인: 비밀번호가 틀리면 LOGIN_FAILED 이고 토큰을 저장하지 않는다")
    void loginWrongPassword() {
        given(accountRepository.findByUserId("staff1")).willReturn(Optional.of(staff));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        assertErrorCode(() -> authService.login(new AuthDto.LoginRequest("staff1", "wrong")), ErrorCode.LOGIN_FAILED);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인: 없는 아이디도 비밀번호 비교를 똑같이 하고 LOGIN_FAILED (응답 시간으로 아이디를 알 수 없게)")
    void loginUnknownUserId() {
        given(accountRepository.findByUserId("nobody")).willReturn(Optional.empty());

        assertErrorCode(() -> authService.login(new AuthDto.LoginRequest("nobody", "pass1234")), ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder).matches(eq("pass1234"), anyString());
    }

    @Test
    @DisplayName("로그인: 72바이트를 넘는 비밀번호는 BCrypt 에 넘기지 않고 LOGIN_FAILED")
    void loginTooLongPassword() {
        given(accountRepository.findByUserId("staff1")).willReturn(Optional.of(staff));

        assertErrorCode(() -> authService.login(new AuthDto.LoginRequest("staff1", "가".repeat(25))),
            ErrorCode.LOGIN_FAILED);
        verifyNoInteractions(passwordEncoder);
    }

    // ── 로그아웃 ──

    @Test
    @DisplayName("로그아웃: 이 계정의 이 토큰 행만 해시로 지운다")
    void logout() {
        authService.logout(1L, "refresh");

        verify(refreshTokenRepository).deleteByTokenHashAndAccountId(AuthService.sha256("refresh"), 1L);
    }

    @Test
    @DisplayName("로그아웃: 리프레시 토큰 쿠키가 없으면 지울 것이 없다")
    void logoutWithoutToken() {
        authService.logout(1L, null);
        authService.logout(1L, "");

        verifyNoInteractions(refreshTokenRepository);
    }

    // ── 비밀번호 변경 ──

    @Test
    @DisplayName("비밀번호 변경: 새 해시로 바꾸고 이 기기 토큰만 남긴다")
    void changePassword() {
        given(accountRepository.findById(1L)).willReturn(Optional.of(staff));
        given(passwordEncoder.matches("pass1234", "hash")).willReturn(true);
        given(passwordEncoder.encode("newpass")).willReturn("new-hash");

        authService.changePassword(1L, "refresh", new AuthDto.ChangePasswordRequest("pass1234", "newpass"));

        assertThat(staff.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenRepository).deleteByAccountIdAndTokenHashNot(1L, AuthService.sha256("refresh"));
    }

    @Test
    @DisplayName("비밀번호 변경: 리프레시 토큰 쿠키가 없으면 그 계정의 토큰을 모두 지운다")
    void changePasswordWithoutToken() {
        given(accountRepository.findById(1L)).willReturn(Optional.of(staff));
        given(passwordEncoder.matches("pass1234", "hash")).willReturn(true);
        given(passwordEncoder.encode("newpass")).willReturn("new-hash");

        authService.changePassword(1L, null, new AuthDto.ChangePasswordRequest("pass1234", "newpass"));

        verify(refreshTokenRepository).deleteByAccountId(1L);
    }

    @Test
    @DisplayName("비밀번호 변경: 현재 비밀번호가 틀리면 PASSWORD_MISMATCH 이고 아무것도 바꾸지 않는다")
    void changePasswordWrongCurrent() {
        given(accountRepository.findById(1L)).willReturn(Optional.of(staff));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        assertErrorCode(() -> authService.changePassword(1L, "refresh",
            new AuthDto.ChangePasswordRequest("wrong", "newpass")), ErrorCode.PASSWORD_MISMATCH);
        assertThat(staff.getPasswordHash()).isEqualTo("hash");
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("비밀번호 변경: 토큰의 계정이 지워졌으면 UNAUTHORIZED")
    void changePasswordAccountGone() {
        given(accountRepository.findById(1L)).willReturn(Optional.empty());

        assertErrorCode(() -> authService.changePassword(1L, "refresh",
            new AuthDto.ChangePasswordRequest("pass1234", "newpass")), ErrorCode.UNAUTHORIZED);
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
