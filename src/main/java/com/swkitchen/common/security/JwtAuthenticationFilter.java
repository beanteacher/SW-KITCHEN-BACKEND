package com.swkitchen.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * 액세스 토큰 쿠키가 유효하면 로그인 상태로 만든다. principal 은 계정 id.
 * 토큰이 없거나 틀리면 그냥 넘기고, 로그인이 필요한 경로면 Security 가 401 을 준다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final SecurityProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS_TOKEN);
        if (cookie != null) {
            jwtProvider.parse(cookie.getValue()).ifPresent(claims -> {
                var authorities = properties.permissionsOf(claims.role()).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(claims.accountId(), null, authorities));
            });
        }
        chain.doFilter(request, response);
    }
}
