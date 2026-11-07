package com.swkitchen.common.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TurnstileClient {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final RestClient REST_CLIENT = RestClient.builder()
        .baseUrl(VERIFY_URL)
        .requestFactory(requestFactory())
        .build();

    private final SecurityProperties properties;

    public boolean verify(String token, String ip) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.captcha().secretKey());
        form.add("response", token);
        form.add("remoteip", ip);
        try {
            VerifyResponse response = REST_CLIENT.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(VerifyResponse.class);
            return response != null && response.success();
        } catch (RestClientException e) {
            // 업체에 닿지 않으면 막는다. 보안문자는 5번 틀린 아이디만 보므로 다른 로그인은 그대로 된다
            log.warn("보안문자 확인 요청 실패: {}", e.getMessage());
            return false;
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerifyResponse(boolean success) {
    }
}
