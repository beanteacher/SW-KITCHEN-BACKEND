package com.swkitchen.member.controller;

import static com.swkitchen.common.security.AuthCookies.VERIFIED_IDENTITY;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.security.AuthCookies;
import com.swkitchen.member.dto.MyPageDto;
import com.swkitchen.member.service.MyPageService;
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
@RequestMapping("/api/v1/member/me")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;
    private final AuthCookies authCookies;

    @GetMapping
    public ResponseEntity<ApiResponse<MyPageDto.MeResponse>> me(@AuthenticationPrincipal Long accountId) {
        MyPageDto.MeResponse me = myPageService.me(accountId);

        return ResponseEntity.ok(ApiResponse.success(me));
    }

    @PatchMapping(value = "/marketing", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> changeMarketing(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody MyPageDto.MarketingRequest request) {
        myPageService.changeMarketing(accountId, request);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/phone")
    public ResponseEntity<ApiResponse<Void>> changePhone(
            @AuthenticationPrincipal Long accountId,
            @CookieValue(name = VERIFIED_IDENTITY, required = false) String verifiedIdentity) {
        myPageService.changePhone(accountId, verifiedIdentity);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.clearVerifiedIdentity().toString())
            .body(ApiResponse.success(null));
    }

    @PostMapping(value = "/withdraw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody MyPageDto.WithdrawRequest request) {
        myPageService.withdraw(accountId, request, false);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.clearAccessToken().toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.clearRefreshToken().toString())
            .header(HttpHeaders.SET_COOKIE, authCookies.clearVerifiedIdentity().toString())
            .body(ApiResponse.success(null));
    }
}
