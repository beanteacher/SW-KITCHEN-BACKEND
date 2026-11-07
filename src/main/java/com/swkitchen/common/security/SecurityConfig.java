package com.swkitchen.common.security;

import com.swkitchen.common.audit.PrivacyAuditFilter;
import com.swkitchen.common.audit.PrivacyAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final SecurityProperties properties;
    private final SecurityErrorHandler errorHandler;
    private final PrivacyAuditLogger auditLogger;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // CSRF 는 SameSite=Lax 쿠키와 JSON 본문만 받는 것으로 막는다. JSON 이 아닌 이미지 업로드는 아래 헤더 검사로 막는다
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 공개 경로는 이 목록이 전부다. 나머지는 로그인이 필요하다
            .authorizeHttpRequests(auth -> auth
                // 다른 사이트의 폼은 이 헤더를 붙일 수 없다. 로그인했어도 없으면 403
                .requestMatchers(SecurityConfig::isUploadWithoutAjaxHeader).denyAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/product/**", "/api/v1/category/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/identity-verification", "/api/v1/member").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/member/user-id-check").permitAll()
                .requestMatchers("/actuator/health", "/error").permitAll()
                // API 문서. 운영은 application-prod.yml 에서 꺼서 이 주소가 없다
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/admin/product/**", "/api/v1/admin/category/**", "/api/v1/admin/image/**")
                    .hasAuthority("PRODUCT_MANAGE")
                .requestMatchers("/api/v1/admin/member/**").hasAuthority("MEMBER_READ")
                .requestMatchers("/api/v1/admin/staff/**").hasAuthority("STAFF_MANAGE")
                // 새 백오피스 경로는 권한을 정해 위에 더하기 전까지 막는다
                .requestMatchers("/api/v1/admin/**").denyAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, properties), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new PrivacyAuditFilter(auditLogger), JwtAuthenticationFilter.class)
            .build();
    }

    private static boolean isUploadWithoutAjaxHeader(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
            && "/api/v1/admin/image".equals(request.getRequestURI())
            && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
