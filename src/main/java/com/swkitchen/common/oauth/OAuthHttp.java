package com.swkitchen.common.oauth;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class OAuthHttp {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    static final RestClient CLIENT = RestClient.builder().requestFactory(requestFactory()).build();

    private OAuthHttp() {
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }
}
