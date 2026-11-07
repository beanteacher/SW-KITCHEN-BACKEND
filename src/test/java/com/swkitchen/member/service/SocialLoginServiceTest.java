package com.swkitchen.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialLoginServiceTest {

    @Test
    @DisplayName("돌아갈 주소는 우리 사이트 경로만 받고, 다른 사이트로 가는 주소는 / 로 바꾼다")
    void safeReturnUrl() {
        assertThat(SocialLoginService.safeReturnUrl("/mypage")).isEqualTo("/mypage");
        assertThat(SocialLoginService.safeReturnUrl(null)).isEqualTo("/");
        assertThat(SocialLoginService.safeReturnUrl("https://evil.com")).isEqualTo("/");
        assertThat(SocialLoginService.safeReturnUrl("//evil.com")).isEqualTo("/");
        assertThat(SocialLoginService.safeReturnUrl("/\\evil.com")).isEqualTo("/");
    }
}
