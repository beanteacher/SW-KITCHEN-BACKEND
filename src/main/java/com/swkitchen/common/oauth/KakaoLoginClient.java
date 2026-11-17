package com.swkitchen.common.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoLoginClient implements SocialLoginClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final OAuthProperties properties;

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("response_type", "code")
            .queryParam("client_id", properties.kakao().clientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .encode()
            .toUriString();
    }

    @Override
    public Optional<String> fetchUserId(String code, String state, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.kakao().clientId());
        form.add("client_secret", properties.kakao().clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        try {
            TokenResponse token = OAuthHttp.CLIENT.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
            UserResponse user = OAuthHttp.CLIENT.get()
                .uri(USER_URL)
                .header("Authorization", "Bearer " + token.accessToken())
                .retrieve()
                .body(UserResponse.class);
            return Optional.ofNullable(user).map(UserResponse::id).map(String::valueOf);
        } catch (RestClientException | NullPointerException e) {
            log.warn("카카오 로그인 실패: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserResponse(Long id) {
    }
}
