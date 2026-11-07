package com.swkitchen.member.dto;

import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Gender;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import com.swkitchen.member.domain.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class IdentityVerificationDto {

    // ── API 요청·응답 (Request / Response) ──

    public record VerifyRequest(
            @NotBlank(message = "본인확인 번호가 없습니다.") @Size(max = 64, message = "본인확인 번호가 올바르지 않습니다.")
            String identityVerificationId,
            @NotNull(message = "본인확인 목적이 없습니다.") VerificationPurpose purpose) {
    }

    // 방금 본인확인한 본인에게만 보여주는 값이라 가리지 않는다
    public record VerifyResponse(String name, LocalDate birthDate, String phone, ExistingAccount existingAccount) {
    }

    // 소셜로만 가입했으면 maskedUserId 가 null
    public record ExistingAccount(String maskedUserId, List<SocialProvider> linkedProviders, OffsetDateTime createdAt) {

        private static final int VISIBLE_CHARS = 3;

        public static ExistingAccount from(Account account) {
            return new ExistingAccount(
                mask(account.getUserId()),
                account.getSocialAccounts().stream().map(SocialAccount::getProvider).toList(),
                account.getCreatedAt().atZone(MemberDto.ZONE).toOffsetDateTime());
        }

        static String mask(String userId) {
            if (userId == null) {
                return null;
            }
            int visible = Math.min(VISIBLE_CHARS, userId.length());
            return userId.substring(0, visible) + "*".repeat(userId.length() - visible);
        }
    }

    // ── 서비스 → 컨트롤러 (Result) ──

    // cookie 는 verified_identity 쿠키에 그대로 넣는 암호문이다
    public record VerifyResult(VerifyResponse response, String cookie, Duration cookieMaxAge) {
    }

    // ── 쿠키 (verified_identity) ──

    // 서버만 풀 수 있게 암호화해 쿠키로 들고 다닌다. CI 는 해시만 담는다
    public record VerifiedIdentity(
            String ciHash,
            String name,
            LocalDate birthDate,
            Gender gender,
            String phone,
            VerificationPurpose purpose,
            Instant expiresAt) {
    }
}
