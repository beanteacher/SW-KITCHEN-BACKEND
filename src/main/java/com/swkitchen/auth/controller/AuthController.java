package com.swkitchen.auth.controller;

import static com.swkitchen.common.security.AuthCookies.REFRESH_TOKEN;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.security.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<ApiResponse<AuthDto.LoginResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest httpRequest) {
        // 앞에 프록시를 두면 프록시가 넘기는 원래 IP 로 바꿔야 한다
        AuthDto.LoginResult result = authService.login(request, httpRequest.getRemoteAddr());

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(result.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(result.refreshToken()).toString())
            .body(ApiResponse.success(AuthDto.LoginResponse.from(result)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(@CookieValue(name = REFRESH_TOKEN, required = false) String refreshToken) {
        AuthDto.RefreshResult result = authService.refresh(refreshToken);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(result.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(result.refreshToken()).toString())
            .body(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthDto.MeResponse>> me(@AuthenticationPrincipal Long accountId) {
        AuthDto.MeResponse me = authService.me(accountId);

        return ResponseEntity.ok(ApiResponse.success(me));
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

    @PatchMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long accountId,
            @CookieValue(name = REFRESH_TOKEN, required = false) String refreshToken,
            @Valid @RequestBody AuthDto.ChangePasswordRequest request) {
        authService.changePassword(accountId, refreshToken, request);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
