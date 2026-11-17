package com.swkitchen.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// privacyKey: DB 의 개인정보, cookieKey: 본인확인·소셜 가입 쿠키. 하나가 새도 다른 쪽은 안전하게 키를 나눈다
@ConfigurationProperties("encryption")
public record EncryptionProperties(String privacyKey, String cookieKey) {
}
