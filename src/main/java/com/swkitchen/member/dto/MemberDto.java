package com.swkitchen.member.dto;

import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public class MemberDto {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // ── API 요청·응답 (Request / Response) ──

    public record AdminSearchRequest(@Size(max = 20, message = "검색어는 20자 이하입니다.") String keyword) {

        public AdminSearchRequest {
            keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        }
    }

    // 휴대폰 번호·생년월일·성별·마케팅 동의는 직원에게 보여주지 않는다
    public record AdminSummaryResponse(
            Long id,
            String userId,
            String name,
            List<SocialProvider> linkedProviders,
            OffsetDateTime createdAt) {

        public static AdminSummaryResponse from(Account account) {
            List<SocialProvider> providers = account.getSocialAccounts().stream()
                .map(SocialAccount::getProvider)
                .toList();

            return new AdminSummaryResponse(
                account.getId(),
                account.getUserId(),
                account.getName(),
                providers,
                account.getCreatedAt().atZone(ZONE).toOffsetDateTime());
        }
    }
}
