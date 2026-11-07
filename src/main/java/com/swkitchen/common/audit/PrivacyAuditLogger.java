package com.swkitchen.common.audit;

import com.swkitchen.member.domain.Account;
import com.swkitchen.member.repository.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrivacyAuditLogger {

    private static final int MAX_PATH = 200;
    private static final int MAX_QUERY = 500;

    private final PrivacyAuditLogRepository privacyAuditLogRepository;
    private final AccountRepository accountRepository;

    // 기록이 안 돼도 응답은 그대로 준다. 로그에는 검색어(개인정보일 수 있다)를 남기지 않는다
    public void record(Long accountId, HttpServletRequest request, int status) {
        AuditAction action = AuditAction.of(request.getMethod());
        if (action == null) {
            return;
        }
        try {
            String userId = accountRepository.findById(accountId).map(Account::getUserId).orElse("");
            privacyAuditLogRepository.save(PrivacyAuditLog.create(
                accountId,
                userId,
                request.getRemoteAddr(),
                action,
                request.getMethod(),
                cut(request.getRequestURI(), MAX_PATH),
                cut(decode(request.getQueryString()), MAX_QUERY),
                status));
        } catch (RuntimeException e) {
            log.error("개인정보 열람 기록을 남기지 못했습니다: accountId={}, {} {}",
                accountId, request.getMethod(), request.getRequestURI(), e);
        }
    }

    private static String decode(String query) {
        if (query == null) {
            return null;
        }
        try {
            return URLDecoder.decode(query, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return query;
        }
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
