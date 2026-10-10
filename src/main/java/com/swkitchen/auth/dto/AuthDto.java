package com.swkitchen.auth.dto;

import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class AuthDto {

    // 로그인은 형식 검사 없이 길이만 막는다. 틀린 형식은 어차피 LOGIN_FAILED 다
    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") @Size(max = 20, message = "아이디는 20자 이하입니다.") String userId,
            @NotEmpty(message = "비밀번호를 입력해 주세요.") @Size(max = 72, message = "비밀번호는 72자 이하입니다.") String password) {
    }

    // passwordChangeRecommended: 첫 관리자 비밀번호 그대로 로그인했으면 true. 화면이 변경 안내만 띄운다
    public record LoginResponse(Long id, String userId, Role role, boolean passwordChangeRecommended) {

        public static LoginResponse of(Account account, boolean passwordChangeRecommended) {
            return new LoginResponse(account.getId(), account.getUserId(), account.getRole(), passwordChangeRecommended);
        }
    }

    // 서비스 → 컨트롤러 전달용. 토큰은 응답 본문이 아니라 쿠키로 나간다
    public record LoginResult(LoginResponse account, String accessToken, String refreshToken) {}
}
