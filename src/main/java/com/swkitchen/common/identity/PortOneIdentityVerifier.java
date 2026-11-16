package com.swkitchen.common.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    // 시뮬레이터가 비워 주는 칸에 넣는 테스트 값 (fillSimulatorBlanks 를 켰을 때만)
    private static final String SIMULATOR_PHONE = "01000000000";
    private static final String SIMULATOR_GENDER = "MALE";
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
            if (response == null) {
                return Optional.empty();
            }
            if (properties.fillSimulatorBlanks()) {
                response = response.withSimulatorBlanksFilled();
            }
            Optional<VerifiedPerson> person = response.toVerifiedPerson();
            if (person.isEmpty()) {
                // 값은 개인정보라 남기지 않고, 어느 칸이 비었는지만 남긴다
                log.warn("본인확인 결과를 쓸 수 없음: id={}, status={}, 빈 칸={}", identityVerificationId, response.status(), response.blankFields());
            }
            return person;
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

        Response withSimulatorBlanksFilled() {
            if (verifiedCustomer == null) {
                return this;
            }
            Customer filled = new Customer(
                verifiedCustomer.ci(),
                verifiedCustomer.name(),
                verifiedCustomer.gender() == null ? SIMULATOR_GENDER : verifiedCustomer.gender(),
                verifiedCustomer.birthDate(),
                verifiedCustomer.phoneNumber() == null ? SIMULATOR_PHONE : verifiedCustomer.phoneNumber());
            return new Response(id, status, filled, verifiedAt);
        }

        List<String> blankFields() {
            if (verifiedCustomer == null) {
                return List.of("verifiedCustomer");
            }
            List<String> blanks = new ArrayList<>();
            if (verifiedAt == null) {
                blanks.add("verifiedAt");
            }
            if (verifiedCustomer.ci() == null) {
                blanks.add("ci");
            }
            if (verifiedCustomer.phoneNumber() == null) {
                blanks.add("phoneNumber");
            }
            if (verifiedCustomer.birthDate() == null) {
                blanks.add("birthDate");
            }
            if (!"MALE".equals(verifiedCustomer.gender()) && !"FEMALE".equals(verifiedCustomer.gender())) {
                blanks.add("gender");
            }
            return blanks;
        }

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
