package com.swkitchen.member.controller;

import static com.swkitchen.common.security.AuthCookies.VERIFIED_IDENTITY;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.security.AuthCookies;
import com.swkitchen.member.dto.SignupDto;
import com.swkitchen.member.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final SignupService signupService;
    private final AuthCookies authCookies;

    @GetMapping("/user-id-check")
    public ResponseEntity<ApiResponse<SignupDto.UserIdCheckResponse>> checkUserId(@Valid SignupDto.UserIdCheckRequest request) {
        SignupDto.UserIdCheckResponse result = signupService.checkUserId(request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/find-id")
    public ResponseEntity<ApiResponse<SignupDto.FindIdResponse>> findId(
            @CookieValue(name = VERIFIED_IDENTITY, required = false) String verifiedIdentity) {
        SignupDto.FindIdResponse result = signupService.findId(verifiedIdentity);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping(value = "/password-reset", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @CookieValue(name = VERIFIED_IDENTITY, required = false) String verifiedIdentity,
            @Valid @RequestBody SignupDto.PasswordResetRequest request) {
        signupService.resetPassword(verifiedIdentity, request);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.clearVerifiedIdentity().toString())
            .body(ApiResponse.success(null));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthDto.LoginResponse>> signup(
            @CookieValue(name = VERIFIED_IDENTITY, required = false) String verifiedIdentity,
            @Valid @RequestBody SignupDto.SignupRequest request) {
        AuthDto.LoginResult result = signupService.signup(verifiedIdentity, request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(result.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(result.refreshToken()).toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.clearVerifiedIdentity().toString())
            .body(ApiResponse.success(AuthDto.LoginResponse.from(result)));
    }
}
