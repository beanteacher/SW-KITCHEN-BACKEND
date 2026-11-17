package com.swkitchen.common.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 고객 개인정보를 다루는 백오피스 경로에 온 요청을 응답이 끝난 뒤 기록한다. 권한이 없어 거부된 요청도 남긴다.
 * 개인정보를 다루는 기능이 생기면 그 경로를 AUDITED_PATHS 에 더한다
 */
@RequiredArgsConstructor
public class PrivacyAuditFilter extends OncePerRequestFilter {

    private static final List<String> AUDITED_PATHS = List.of("/api/v1/admin/member");

    private final PrivacyAuditLogger auditLogger;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return AUDITED_PATHS.stream().noneMatch(path -> uri.equals(path) || uri.startsWith(path + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            // 로그인하지 않은 요청은 본 사람이 없어 남기지 않는다
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Long accountId) {
                auditLogger.record(accountId, request, response.getStatus());
            }
        }
    }
}
