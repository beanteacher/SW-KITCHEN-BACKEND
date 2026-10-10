package com.swkitchen.auth.controller;

import static com.swkitchen.common.security.AuthCookies.REFRESH_TOKEN;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.security.AuthCookies;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthDto.LoginResponse>> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        AuthDto.LoginResult result = authService.login(request);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(result.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(result.refreshToken()).toString())
            .body(ApiResponse.success(result.account()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long accountId,
            @CookieValue(name = REFRESH_TOKEN, required = false) String refreshToken) {
        authService.logout(accountId, refreshToken);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.clearAccessToken().toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.clearRefreshToken().toString())
            .body(ApiResponse.success(null));
    }
}
