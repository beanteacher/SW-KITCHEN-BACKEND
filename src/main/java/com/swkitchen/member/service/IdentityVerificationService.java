package com.swkitchen.member.service;

import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.common.identity.IdentityVerifier;
import com.swkitchen.common.identity.VerifiedPerson;
import com.swkitchen.common.security.CryptoUtil;
import com.swkitchen.common.security.SealedCookieCodec;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.Gender;
import com.swkitchen.member.domain.UsedIdentityVerification;
import com.swkitchen.member.domain.VerificationPurpose;
import com.swkitchen.member.dto.IdentityVerificationDto;
import com.swkitchen.member.dto.MemberDto;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.UsedIdentityVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class IdentityVerificationService {

    public static final Duration VALID_TIME = Duration.ofMinutes(10);
    private static final int MIN_SIGNUP_AGE = 14;

    private final IdentityVerifier identityVerifier;
    private final UsedIdentityVerificationRepository usedIdentityVerificationRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final SealedCookieCodec sealedCookieCodec;

    /** 업체에 결과를 묻고, 한 번 쓴 건은 다시 받지 않는다. 결과는 암호화한 쿠키 값으로 돌려준다 */
    public IdentityVerificationDto.VerifyResult verify(IdentityVerificationDto.VerifyRequest request) {
        Instant now = Instant.now();
        VerifiedPerson person = identityVerifier.fetch(request.identityVerificationId())
            .filter(p -> p.verifiedAt().plus(VALID_TIME).isAfter(now))
            .orElseThrow(() -> new AppException(ErrorCode.IDENTITY_VERIFICATION_FAILED));
        markUsed(person.verificationId());
        if (request.purpose() == VerificationPurpose.SIGNUP && ageOf(person.birthDate()) < MIN_SIGNUP_AGE) {
            throw new AppException(ErrorCode.UNDER_AGE);
        }

        String ciHash = CryptoUtil.sha256(person.ci());
        Instant expiresAt = person.verifiedAt().plus(VALID_TIME);
        IdentityVerificationDto.VerifiedIdentity verified = new IdentityVerificationDto.VerifiedIdentity(
            ciHash,
            person.name(),
            person.birthDate(),
            Gender.valueOf(person.gender()),
            person.phone(),
            request.purpose(),
            expiresAt);
        IdentityVerificationDto.ExistingAccount existing = accountIdentityRepository.findByCiHash(ciHash)
            .map(AccountIdentity::getAccount)
            .map(IdentityVerificationDto.ExistingAccount::from)
            .orElse(null);

        return new IdentityVerificationDto.VerifyResult(
            new IdentityVerificationDto.VerifyResponse(person.name(), person.birthDate(), person.phone(), existing),
            sealedCookieCodec.seal(verified),
            Duration.between(now, expiresAt));
    }

    /** 쿠키가 없거나, 만료됐거나, 다른 목적으로 받은 본인확인이면 IDENTITY_VERIFICATION_REQUIRED */
    @Transactional(readOnly = true)
    public IdentityVerificationDto.VerifiedIdentity require(String cookie, VerificationPurpose purpose) {
        return open(cookie)
            .filter(verified -> verified.purpose() == purpose)
            .orElseThrow(() -> new AppException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED));
    }

    private Optional<IdentityVerificationDto.VerifiedIdentity> open(String cookie) {
        return sealedCookieCodec.open(cookie, IdentityVerificationDto.VerifiedIdentity.class)
            .filter(verified -> verified.expiresAt().isAfter(Instant.now()));
    }

    private void markUsed(String verificationId) {
        try {
            usedIdentityVerificationRepository.saveAndFlush(
                UsedIdentityVerification.create(verificationId, LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
    }

    private static int ageOf(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now(MemberDto.ZONE)).getYears();
    }
}
