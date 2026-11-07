package com.swkitchen.common.identity;

import java.time.Instant;
import java.time.LocalDate;

// gender 는 M / F
public record VerifiedPerson(
        String verificationId,
        String ci,
        String name,
        LocalDate birthDate,
        String gender,
        String phone,
        Instant verifiedAt) {
}
