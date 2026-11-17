package com.swkitchen.member.dto;

import com.swkitchen.common.validation.MaxBytes;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class MyPageDto {

    // ── API 요청·응답 (Request / Response) ──

    // hasPassword 가 false 면 화면은 비밀번호 변경을 숨긴다
    public record MeResponse(
            String userId,
            String name,
            LocalDate birthDate,
            String phone,
            List<SocialProvider> linkedProviders,
            boolean marketingAgreed,
            boolean hasPassword,
            OffsetDateTime createdAt) {

        public static MeResponse of(Account account, AccountIdentity identity) {
            return new MeResponse(
                account.getUserId(),
                account.getName(),
                identity.getBirthDate(),
                identity.getPhone(),
                account.getSocialAccounts().stream().map(SocialAccount::getProvider).toList(),
                identity.isMarketingAgreed(),
                account.getPasswordHash() != null,
                account.getCreatedAt().atZone(MemberDto.ZONE).toOffsetDateTime());
        }
    }

    public record MarketingRequest(@NotNull(message = "동의 여부를 골라 주세요.") Boolean agreed) {
    }

    // 소셜로만 가입한 계정은 password 를 보내지 않는다
    public record WithdrawRequest(
            @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (영문 72자, 한글 24자 이하)") String password) {
    }
}
