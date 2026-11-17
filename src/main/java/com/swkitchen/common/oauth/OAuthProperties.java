package com.swkitchen.common.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * redirectBaseUrl: 카카오·네이버가 돌려보낼 우리 주소의 앞부분 (화면 서버). 뒤에 /api/v1/auth/oauth/{provider}/callback 이 붙는다.
 * 화면 서버가 /api 를 백엔드로 넘기므로 쿠키도 화면 주소에 저장된다
 */
@ConfigurationProperties("oauth")
public record OAuthProperties(String redirectBaseUrl, Client kakao, Client naver) {

    public record Client(String clientId, String clientSecret) {}
}
