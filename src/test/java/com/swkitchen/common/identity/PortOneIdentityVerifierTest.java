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
}
