package com.swkitchen.common.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 포트원 V2 본인인증 단건 조회 (KCP) */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneIdentityVerifier implements IdentityVerifier {

    private static final String BASE_URL = "https://api.portone.io";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final RestClient REST_CLIENT = RestClient.builder()
        .baseUrl(BASE_URL)
        .requestFactory(requestFactory())
        .build();

    private final PortOneProperties properties;

    @Override
    public Optional<VerifiedPerson> fetch(String identityVerificationId) {
        try {
            Response response = REST_CLIENT.get()
                .uri("/identity-verifications/{id}", identityVerificationId)
                .header("Authorization", "PortOne " + properties.apiSecret())
                .retrieve()
                .body(Response.class);
            return Optional.ofNullable(response).flatMap(Response::toVerifiedPerson);
        } catch (RestClientException e) {
            // 결과에 개인정보가 있어 응답 본문은 남기지 않는다
            log.warn("본인확인 결과 조회 실패: id={}, {}", identityVerificationId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String id, String status, Customer verifiedCustomer, Instant verifiedAt) {

        Optional<VerifiedPerson> toVerifiedPerson() {
            if (!"VERIFIED".equals(status) || verifiedCustomer == null || verifiedAt == null) {
                return Optional.empty();
            }
            String gender = switch (String.valueOf(verifiedCustomer.gender())) {
                case "MALE" -> "M";
                case "FEMALE" -> "F";
                default -> null;
            };
            // CI 나 휴대폰 번호가 없으면 같은 사람 확인·연락을 할 수 없다 (KCP 가맹점 설정에서 CI 를 켜야 한다)
            if (verifiedCustomer.ci() == null || verifiedCustomer.phoneNumber() == null
                    || verifiedCustomer.birthDate() == null || gender == null) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedPerson(
                id,
                verifiedCustomer.ci(),
                verifiedCustomer.name(),
                verifiedCustomer.birthDate(),
                gender,
                verifiedCustomer.phoneNumber(),
                verifiedAt));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Customer(String ci, String name, String gender, LocalDate birthDate, String phoneNumber) {
    }
}
