package com.swkitchen.auth.service;

import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.RefreshToken;
import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.repository.AccountRepository;
import com.swkitchen.auth.repository.RefreshTokenRepository;
import com.swkitchen.common.dto.ErrorResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.common.security.CryptoUtil;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.common.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String DUMMY_HASH = "$2a$10$X5F6s3P1izaTip40f4.oO.ibMUurwLFyw3iE2.A0RALdTMsOOZQA6";

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SecurityProperties properties;

    public AuthDto.LoginResult login(AuthDto.LoginRequest request) {
        Account account = accountRepository.findByUserId(request.userId()).orElse(null);
        String passwordHash = account == null ? DUMMY_HASH : account.getPasswordHash(); // 없는 아이디도 비밀번호 비교를 똑같이 해서, 응답 시간으로 아이디가 있는지 알 수 없게 한다
        if (isWrongPassword(request.password(), passwordHash) || account == null) {
            throw new AppException(ErrorCode.LOGIN_FAILED);
        }

        String token = createRefreshToken(account);

        return AuthDto.LoginResult.of(
            account,
            isInitialPassword(request.password()),
            jwtProvider.createAccessToken(account.getId(), account.getRole()),
            token);
    }

    @Transactional(readOnly = true)
    public AuthDto.MeResponse me(Long accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        return AuthDto.MeResponse.from(account);
    }

    /** 쓴 리프레시 토큰은 지우고 새로 준다. 같은 토큰을 두 번 쓸 수 없다 */
    public AuthDto.RefreshResult refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        RefreshToken saved = refreshTokenRepository.findByTokenHash(CryptoUtil.sha256(refreshToken))
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        refreshTokenRepository.delete(saved);
        if (saved.isExpired(LocalDateTime.now())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Account account = saved.getAccount();
        String token = createRefreshToken(account);

        return new AuthDto.RefreshResult(
            jwtProvider.createAccessToken(account.getId(), account.getRole()),
            token);
    }

    /** 이 기기의 리프레시 토큰만 지운다. 다른 계정의 토큰은 건드리지 않는다 */
    public void logout(Long accountId, String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenRepository.deleteByTokenHashAndAccountId(CryptoUtil.sha256(refreshToken), accountId);
        }
    }

    /** 바꾸면 이 기기만 남기고 다른 기기는 로그아웃된다 */
    public void changePassword(Long accountId, String refreshToken, AuthDto.ChangePasswordRequest request) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        if (isWrongPassword(request.currentPassword(), account.getPasswordHash())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH,
                List.of(new ErrorResponse.FieldError("currentPassword", ErrorCode.PASSWORD_MISMATCH.getMessage())));
        }

        account.changePassword(passwordEncoder.encode(request.newPassword()));
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenRepository.deleteByAccountIdAndTokenHashNot(accountId, CryptoUtil.sha256(refreshToken));
        } else {
            refreshTokenRepository.deleteByAccountId(accountId);
        }
    }

    // 원문은 쿠키로 돌려주고 DB 에는 해시만 둔다
    private String createRefreshToken(Account account) {
        String token = CryptoUtil.randomToken(REFRESH_TOKEN_BYTES);
        LocalDateTime expiresAt = LocalDateTime.now().plus(properties.jwt().refreshTokenTtl());
        RefreshToken refreshToken = RefreshToken.create(
            account,
            CryptoUtil.sha256(token),
            expiresAt);

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    private boolean isWrongPassword(String password, String passwordHash) {
        // BCrypt 는 72바이트를 넘으면 오류를 낸다. 그런 비밀번호로는 가입할 수 없으니 틀린 것으로 본다
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return true;
        }
        return !passwordEncoder.matches(password, passwordHash);
    }

    private boolean isInitialPassword(String password) {
        SecurityProperties.InitialAdmin admin = properties.initialAdmin();
        if (admin == null || !StringUtils.hasText(admin.password())) {
            return false;
        }
        return CryptoUtil.constantTimeEquals(password, admin.password());
    }
}
