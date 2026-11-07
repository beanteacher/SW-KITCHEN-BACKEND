package com.swkitchen.member.dto;

import com.swkitchen.common.validation.CustomerPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SignupDto {

    public static final String USER_ID_PATTERN = "^[a-z0-9]{4,20}$";
    public static final String USER_ID_MESSAGE = "아이디는 영문 소문자·숫자 4~20자입니다.";

    // ── API 요청·응답 (Request / Response) ──

    public record UserIdCheckRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") @Pattern(regexp = USER_ID_PATTERN, message = USER_ID_MESSAGE)
            String userId) {
    }

    public record UserIdCheckResponse(boolean available) {
    }

    // 비밀번호 확인 칸은 화면에서 맞춘다
    public record SignupRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") @Pattern(regexp = USER_ID_PATTERN, message = USER_ID_MESSAGE)
            String userId,
            @NotEmpty(message = "비밀번호를 입력해 주세요.") @CustomerPassword String password,
            @NotNull(message = "동의 항목을 확인해 주세요.") @Valid Agreements agreements) {
    }

    public record SocialSignupRequest(@NotNull(message = "동의 항목을 확인해 주세요.") @Valid Agreements agreements) {
    }

    public record Agreements(
            @AssertTrue(message = "이용약관에 동의해 주세요.") boolean terms,
            @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.") boolean privacy,
            boolean marketing) {
    }
}
