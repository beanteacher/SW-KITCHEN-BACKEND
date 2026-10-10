package com.swkitchen.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** DB 에는 이미지 키만 두고, 응답에서 공개 주소 앞부분을 붙인다 */
@ConfigurationProperties("image")
public record ImageProperties(String baseUrl) {

    public String urlOf(String key) {
        return baseUrl + "/" + key;
    }
}
