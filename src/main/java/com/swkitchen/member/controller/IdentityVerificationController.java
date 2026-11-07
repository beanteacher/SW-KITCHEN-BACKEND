package com.swkitchen.member.controller;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.security.AuthCookies;
import com.swkitchen.member.dto.IdentityVerificationDto;
import com.swkitchen.member.service.IdentityVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity-verification")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;
    private final AuthCookies authCookies;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<IdentityVerificationDto.VerifyResponse>> verify(
            @Valid @RequestBody IdentityVerificationDto.VerifyRequest request) {
        IdentityVerificationDto.VerifyResult result = identityVerificationService.verify(request);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, authCookies.verifiedIdentity(result.cookie(), result.cookieMaxAge()).toString())
            .body(ApiResponse.success(result.response()));
    }
}
