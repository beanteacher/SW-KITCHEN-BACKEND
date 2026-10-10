package com.swkitchen.auth.service;

import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.RefreshToken;
import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.repository.AccountRepository;
import com.swkitchen.auth.repository.RefreshTokenRepository;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.common.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;
    // 없는 아이디일 때 비교할 BCrypt 해시. 실제 계정과 같은 강도(10)라 걸리는 시간이 같다
    private static final String DUMMY_HASH = "$2a$10$X5F6s3P1izaTip40f4.oO.ibMUurwLFyw3iE2.A0RALdTMsOOZQA6";

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SecurityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthDto.LoginResult login(AuthDto.LoginRequest request) {
        Account account = accountRepository.findByUserId(request.userId()).orElse(null);
        // 없는 아이디도 비밀번호 비교를 똑같이 해서, 응답 시간으로 아이디가 있는지 알 수 없게 한다
        String hash = account != null ? account.getPasswordHash() : DUMMY_HASH;
        if (!passwordMatches(request.password(), hash) || account == null) {
            throw new AppException(ErrorCode.LOGIN_FAILED);
        }

        String refreshToken = newRefreshToken();
        refreshTokenRepository.save(RefreshToken.create(
            account.getId(), sha256(refreshToken), LocalDateTime.now().plus(properties.jwt().refreshTokenTtl())));

        return AuthDto.LoginResult.of(
            account,
            isInitialPassword(request.password()),
            jwtProvider.createAccessToken(account.getId(), account.getRole()),
            refreshToken);
    }

    /** 이 기기의 리프레시 토큰만 지운다. 다른 계정의 토큰은 건드리지 않는다 */
    public void logout(Long accountId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenRepository.deleteByTokenHashAndAccountId(sha256(refreshToken), accountId);
        }
    }

    private boolean passwordMatches(String raw, String hash) {
        // BCrypt 는 72바이트를 넘으면 오류를 낸다. 그런 비밀번호로는 가입할 수 없으니 틀린 것으로 본다
        if (raw.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return passwordEncoder.matches(raw, hash);
    }

    private boolean isInitialPassword(String raw) {
        String initial = properties.initialAdmin() != null ? properties.initialAdmin().password() : null;
        if (initial == null || initial.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), initial.getBytes(StandardCharsets.UTF_8));
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
