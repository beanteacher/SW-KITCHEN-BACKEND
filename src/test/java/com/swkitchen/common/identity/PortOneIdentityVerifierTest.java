package com.swkitchen.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOneIdentityVerifierTest {

    static final Instant VERIFIED_AT = Instant.parse("2026-11-07T01:00:00Z");
    static final PortOneIdentityVerifier.Customer CUSTOMER = new PortOneIdentityVerifier.Customer(
        "ci-value", "김주방", "MALE", LocalDate.of(1990, 5, 12), "01012345678");

    @Test
    @DisplayName("인증 완료 결과를 우리 모양으로 바꾸고, 성별은 M / F 로 바꾼다")
    void verified() {
        var response = new PortOneIdentityVerifier.Response("iv-1", "VERIFIED", CUSTOMER, VERIFIED_AT);

        assertThat(response.toVerifiedPerson()).contains(new VerifiedPerson(
            "iv-1", "ci-value", "김주방", LocalDate.of(1990, 5, 12), "M", "01012345678", VERIFIED_AT));
    }

    @Test
    @DisplayName("완료가 아니거나, CI·휴대폰 번호가 없거나, 성별이 남녀가 아니면 결과가 없다")
    void notUsable() {
        assertThat(new PortOneIdentityVerifier.Response("iv-1", "READY", CUSTOMER, VERIFIED_AT).toVerifiedPerson()).isEmpty();
        assertThat(new PortOneIdentityVerifier.Response("iv-1", "VERIFIED", CUSTOMER, null).toVerifiedPerson()).isEmpty();
        assertThat(new PortOneIdentityVerifier.Response("iv-1", "VERIFIED",
            new PortOneIdentityVerifier.Customer(null, "김주방", "MALE", LocalDate.of(1990, 5, 12), "01012345678"),
            VERIFIED_AT).toVerifiedPerson()).isEmpty();
        assertThat(new PortOneIdentityVerifier.Response("iv-1", "VERIFIED",
            new PortOneIdentityVerifier.Customer("ci", "김주방", "MALE", LocalDate.of(1990, 5, 12), null),
            VERIFIED_AT).toVerifiedPerson()).isEmpty();
        assertThat(new PortOneIdentityVerifier.Response("iv-1", "VERIFIED",
            new PortOneIdentityVerifier.Customer("ci", "김주방", "OTHER", LocalDate.of(1990, 5, 12), "01012345678"),
            VERIFIED_AT).toVerifiedPerson()).isEmpty();
    }

    // KCP 시뮬레이터가 실제로 준 모양: 인증 완료, CI·이름·생년월일은 있고 휴대폰 번호·성별은 비어 있다
    static final PortOneIdentityVerifier.Response SIMULATOR = new PortOneIdentityVerifier.Response(
        "iv-1",
        "VERIFIED",
        new PortOneIdentityVerifier.Customer("simulator__ci_value", "홍길동", null, LocalDate.of(2001, 1, 1), null),
        VERIFIED_AT);

    @Test
    @DisplayName("시뮬레이터 결과는 그대로면 쓸 수 없고, 빈 칸으로 휴대폰 번호와 성별을 알려 준다")
    void simulatorAsIs() {
        assertThat(SIMULATOR.toVerifiedPerson()).isEmpty();
        assertThat(SIMULATOR.blankFields()).containsExactly("phoneNumber", "gender");
    }

    @Test
    @DisplayName("빈 칸 채우기를 켜면 시뮬레이터 결과도 테스트 휴대폰 번호·남성으로 통과한다")
    void simulatorFilled() {
        assertThat(SIMULATOR.withSimulatorBlanksFilled().toVerifiedPerson()).contains(new VerifiedPerson(
            "iv-1", "simulator__ci_value", "홍길동", LocalDate.of(2001, 1, 1), "M", "01000000000", VERIFIED_AT));
    }

    @Test
    @DisplayName("빈 칸 채우기는 이미 있는 값을 바꾸지 않는다")
    void fillKeepsRealValues() {
        var response = new PortOneIdentityVerifier.Response("iv-1", "VERIFIED", CUSTOMER, VERIFIED_AT);

        assertThat(response.withSimulatorBlanksFilled()).isEqualTo(response);
    }
}
