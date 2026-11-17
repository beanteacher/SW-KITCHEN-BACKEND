package com.swkitchen.member.controller;

import static com.swkitchen.common.security.AuthCookies.OAUTH_STATE;
import static com.swkitchen.common.security.AuthCookies.SOCIAL_SIGNUP;
import static com.swkitchen.common.security.AuthCookies.VERIFIED_IDENTITY;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.common.security.AuthCookies;
import com.swkitchen.member.domain.SocialProvider;
import com.swkitchen.member.dto.SignupDto;
import com.swkitchen.member.dto.SocialLoginDto;
import com.swkitchen.member.service.SocialLoginService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SocialLoginController {

    private static final String REAUTH = "reauth";

    private final SocialLoginService socialLoginService;
    private final AuthCookies authCookies;

    @GetMapping("/api/v1/auth/oauth/{provider}")
    public ResponseEntity<Void> start(
            @PathVariable String provider,
            @RequestParam(required = false) String returnUrl,
            @RequestParam(required = false) String purpose) {
        SocialLoginService.Start start = socialLoginService.start(providerOf(provider), returnUrl, REAUTH.equals(purpose));

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(start.authorizeUrl()))
            .header(HttpHeaders.SET_COOKIE,
                authCookies.oauthState(start.stateCookie(), SocialLoginService.STATE_TIME).toString())
            .build();
    }

    @GetMapping("/api/v1/auth/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @CookieValue(name = OAUTH_STATE, required = false) String stateCookie,
            @AuthenticationPrincipal Long accountId) {
        SocialLoginDto.CallbackResult result =
            socialLoginService.callback(providerOf(provider), code, state, stateCookie, accountId);

        var response = ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(result.redirectUrl()))
            .header(HttpHeaders.SET_COOKIE, authCookies.clearOAuthState().toString());
        if (result.login() != null) {
            response.header(HttpHeaders.SET_COOKIE, authCookies.accessToken(result.login().accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(result.login().refreshToken()).toString());
        }
        if (result.socialSignupCookie() != null) {
            response.header(HttpHeaders.SET_COOKIE,
                authCookies.socialSignup(result.socialSignupCookie(), SocialLoginService.SIGNUP_TIME).toString());
        }
        if (result.reauthCookie() != null) {
            response.header(HttpHeaders.SET_COOKIE,
                authCookies.socialReauth(result.reauthCookie(), SocialLoginService.REAUTH_TIME).toString());
        }
        return response.build();
    }

    @PostMapping(value = "/api/v1/member/social", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthDto.LoginResponse>> signup(
            @CookieValue(name = SOCIAL_SIGNUP, required = false) String socialSignup,
            @CookieValue(name = VERIFIED_IDENTITY, required = false) String verifiedIdentity,
            @Valid @RequestBody SignupDto.SocialSignupRequest request) {
        AuthDto.LoginResult result = socialLoginService.signup(socialSignup, verifiedIdentity, request);

        return loggedIn(ResponseEntity.status(HttpStatus.CREATED), result);
    }

    @PostMapping(value = "/api/v1/member/social/link", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthDto.LoginResponse>> link(
            @CookieValue(name = SOCIAL_SIGNUP, required = false) String socialSignup,
            @CookieValue(name = VERIFIED_IDENTITY, required = false) String verifiedIdentity,
            @Valid @RequestBody SocialLoginDto.LinkRequest request) {
        AuthDto.LoginResult result = socialLoginService.link(socialSignup, verifiedIdentity, request);

        return loggedIn(ResponseEntity.ok(), result);
    }

    private ResponseEntity<ApiResponse<AuthDto.LoginResponse>> loggedIn(
            ResponseEntity.BodyBuilder builder,
            AuthDto.LoginResult result) {
        return builder
            .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(result.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(result.refreshToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.clearVerifiedIdentity().toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.clearSocialSignup().toString())
            .body(ApiResponse.success(AuthDto.LoginResponse.from(result)));
    }

    // 주소에는 소문자(kakao, naver)로 온다. 그 밖의 값은 없는 주소다
    private static SocialProvider providerOf(String provider) {
        return switch (provider) {
            case "kakao" -> SocialProvider.KAKAO;
            case "naver" -> SocialProvider.NAVER;
            default -> throw new AppException(ErrorCode.NOT_FOUND);
        };
    }
}
