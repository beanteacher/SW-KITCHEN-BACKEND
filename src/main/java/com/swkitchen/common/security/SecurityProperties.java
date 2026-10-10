package com.swkitchen.common.security;

import com.swkitchen.auth.domain.Role;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security")
public record SecurityProperties(Jwt jwt, Map<Role, List<String>> rolePermissions, InitialAdmin initialAdmin) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record InitialAdmin(String userId, String password) {}

    public List<String> permissionsOf(Role role) {
        return rolePermissions.getOrDefault(role, List.of());
    }
}
