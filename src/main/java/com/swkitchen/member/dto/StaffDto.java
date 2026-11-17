package com.swkitchen.member.dto;

import com.swkitchen.common.validation.MaxBytes;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountStatus;
import com.swkitchen.member.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public class StaffDto {

    // ── API 요청·응답 (Request / Response) ──

    public record CreateRequest(
            @NotBlank(message = "아이디를 입력해 주세요.")
            @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "아이디는 영문 소문자·숫자 4~20자입니다.")
            String userId,
            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 20, message = "이름은 20자 이하입니다.") String name,
            @NotNull(message = "역할을 골라 주세요.") Role role,
            @NotEmpty(message = "비밀번호를 입력해 주세요.")
            @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (영문 72자, 한글 24자 이하)")
            String password) {

        @AssertTrue(message = "직원 역할만 고를 수 있습니다.")
        public boolean isStaffRole() {
            return role == null || role != Role.CUSTOMER;
        }
    }

    // 보낸 값만 바꾼다
    public record UpdateRequest(Role role, AccountStatus status) {

        @AssertTrue(message = "직원 역할만 고를 수 있습니다.")
        public boolean isStaffRole() {
            return role == null || role != Role.CUSTOMER;
        }
    }

    public record PasswordRequest(
            @NotEmpty(message = "비밀번호를 입력해 주세요.")
            @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (영문 72자, 한글 24자 이하)")
            String password) {
    }

    public record Response(Long id, String userId, String name, Role role, AccountStatus status, OffsetDateTime createdAt) {

        public static Response from(Account account) {
            return new Response(
                account.getId(),
                account.getUserId(),
                account.getName(),
                account.getRole(),
                account.getStatus(),
                account.getCreatedAt().atZone(MemberDto.ZONE).toOffsetDateTime());
        }
    }
}
