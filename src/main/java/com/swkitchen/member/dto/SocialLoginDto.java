package com.swkitchen.member.dto;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.member.domain.SocialProvider;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class SocialLoginDto {

    // ── API 요청·응답 (Request / Response) ──

    // 기존 계정이 아이디 가입이면 그 비밀번호
    public record LinkRequest(@Size(max = 72, message = "비밀번호는 72자 이하입니다.") String password) {
    }

    // ── 쿠키 (서버만 풀 수 있게 암호화한다) ──

    // oauth_state: 카카오·네이버에 보낸 state 와 돌아온 뒤 갈 곳. reauth 면 탈퇴 전 다시 확인이다
    public record OAuthState(String state, SocialProvider provider, String returnUrl, boolean reauth, Instant expiresAt) {
    }

    // social_signup: 아직 계정과 잇지 않은 카카오·네이버 회원 번호
    public record SocialSignup(SocialProvider provider, String providerUserId, Instant expiresAt) {
    }

    // social_reauth: 소셜로만 가입한 고객이 탈퇴 전 소셜 로그인을 다시 한 표시
    public record SocialReauth(Long accountId, Instant expiresAt) {
    }

    // ── 서비스 → 컨트롤러 (Result) ──

    // 콜백 결과. 로그인했으면 login, 가입·연결 화면으로 가면 socialSignupCookie, 탈퇴 전 다시 확인이면 reauthCookie 만 채운다
    public record CallbackResult(
            String redirectUrl,
            AuthDto.LoginResult login,
            String socialSignupCookie,
            String reauthCookie) {
    }
}
