package com.swkitchen.common.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverLoginClient implements SocialLoginClient {

    private static final String AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_URL = "https://openapi.naver.com/v1/nid/me";

    private final OAuthProperties properties;

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("response_type", "code")
            .queryParam("client_id", properties.naver().clientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .encode()
            .toUriString();
    }

    @Override
    public Optional<String> fetchUserId(String code, String state, String redirectUri) {
        String tokenUri = UriComponentsBuilder.fromUriString(TOKEN_URL)
            .queryParam("grant_type", "authorization_code")
            .queryParam("client_id", properties.naver().clientId())
            .queryParam("client_secret", properties.naver().clientSecret())
            .queryParam("code", code)
            .queryParam("state", state)
            .encode()
            .toUriString();
        try {
            TokenResponse token = OAuthHttp.CLIENT.post().uri(tokenUri).retrieve().body(TokenResponse.class);
            UserResponse user = OAuthHttp.CLIENT.get()
                .uri(USER_URL)
                .header("Authorization", "Bearer " + token.accessToken())
                .retrieve()
                .body(UserResponse.class);
            return Optional.ofNullable(user).map(UserResponse::response).map(Profile::id);
        } catch (RestClientException | NullPointerException e) {
            log.warn("네이버 로그인 실패: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserResponse(Profile response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String id) {
    }
}
