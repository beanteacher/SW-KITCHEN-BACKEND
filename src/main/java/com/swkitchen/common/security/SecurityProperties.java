package com.swkitchen.common.security;

import com.swkitchen.auth.domain.Role;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security")
// cookieSecure: 토큰 쿠키의 Secure. 안 적으면 켠다. Safari 는 http://localhost 에서 Secure 쿠키를 저장하지 않아 로컬만 끈다
public record SecurityProperties(
        Jwt jwt,
        Map<Role, List<String>> rolePermissions,
        InitialAdmin initialAdmin,
        Boolean cookieSecure) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record InitialAdmin(String userId, String password) {}

    public boolean isCookieSecure() {
        return !Boolean.FALSE.equals(cookieSecure);
    }

    public List<String> permissionsOf(Role role) {
        return rolePermissions.getOrDefault(role, List.of());
    }
}
