package com.swkitchen.auth.dto;

import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.common.validation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class AuthDto {

    // ── API 요청·응답 (Request / Response) ──

    // 로그인은 형식 검사 없이 길이만 막는다. 틀린 형식은 어차피 LOGIN_FAILED 다
    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") @Size(max = 20, message = "아이디는 20자 이하입니다.") String userId,
            @NotEmpty(message = "비밀번호를 입력해 주세요.") @Size(max = 72, message = "비밀번호는 72자 이하입니다.") String password) {
    }

    // 새 비밀번호 확인은 화면에서 한다
    public record ChangePasswordRequest(
            @NotEmpty(message = "현재 비밀번호를 입력해 주세요.") @Size(max = 72, message = "비밀번호는 72자 이하입니다.") String currentPassword,
            @NotEmpty(message = "새 비밀번호를 입력해 주세요.") @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (영문 72자, 한글 24자 이하)") String newPassword) {
    }

    // passwordChangeRecommended: 첫 관리자 비밀번호 그대로 로그인했으면 true. 화면이 변경 안내만 띄운다
    public record LoginResponse(Long id, String userId, Role role, boolean passwordChangeRecommended) {

        public static LoginResponse from(LoginResult result) {
            return new LoginResponse(result.id(), result.userId(), result.role(), result.passwordChangeRecommended());
        }
    }

    // 토큰을 자바스크립트가 못 읽으므로 화면은 이걸로 누가 로그인했는지 안다
    public record MeResponse(Long id, String userId, Role role) {

        public static MeResponse from(Account account) {
            return new MeResponse(account.getId(), account.getUserId(), account.getRole());
        }
    }

    // ── 서비스 → 컨트롤러 (Result) ──

    // 토큰은 응답 본문이 아니라 쿠키로 나간다
    public record LoginResult(
            Long id,
            String userId,
            Role role,
            boolean passwordChangeRecommended,
            String accessToken,
            String refreshToken) {

        public static LoginResult of(Account account, boolean passwordChangeRecommended, String accessToken, String refreshToken) {
            return new LoginResult(
                account.getId(),
                account.getUserId(),
                account.getRole(),
                passwordChangeRecommended,
                accessToken,
                refreshToken);
        }
    }

    // 리프레시 때 두 토큰 모두 새로 준다. 둘 다 쿠키로 나간다
    public record RefreshResult(String accessToken, String refreshToken) {
    }
}
