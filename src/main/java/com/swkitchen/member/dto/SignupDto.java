package com.swkitchen.member.dto;

import com.swkitchen.common.validation.CustomerPassword;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

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

    // 소셜로만 가입했으면 userId 가 null. 본인확인한 본인에게만 보여주므로 아이디를 가리지 않는다
    public record FindIdResponse(String userId, List<SocialProvider> linkedProviders, OffsetDateTime createdAt) {

        public static FindIdResponse from(Account account) {
            return new FindIdResponse(
                account.getUserId(),
                account.getSocialAccounts().stream().map(SocialAccount::getProvider).toList(),
                account.getCreatedAt().atZone(MemberDto.ZONE).toOffsetDateTime());
        }
    }

    public record PasswordResetRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") @Size(max = 20, message = "아이디는 20자 이하입니다.") String userId,
            @NotEmpty(message = "새 비밀번호를 입력해 주세요.") @CustomerPassword String newPassword) {
    }

    public record Agreements(
            @AssertTrue(message = "이용약관에 동의해 주세요.") boolean terms,
            @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.") boolean privacy,
            boolean marketing) {
    }
}
