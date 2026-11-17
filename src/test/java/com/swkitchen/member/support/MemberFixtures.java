package com.swkitchen.member.support;

import com.swkitchen.common.identity.VerifiedPerson;
import java.time.Instant;
import java.time.LocalDate;

/** 회원 테스트가 함께 쓰는 본인확인 결과 */
public final class MemberFixtures {

    private MemberFixtures() {
    }

    public static VerifiedPerson person(String verificationId, String ci) {
        return person(verificationId, ci, LocalDate.of(1990, 5, 12));
    }

    public static VerifiedPerson person(String verificationId, String ci, LocalDate birthDate) {
        return new VerifiedPerson(verificationId, ci, "김주방", birthDate, "M", "01012345678", Instant.now());
    }
}
