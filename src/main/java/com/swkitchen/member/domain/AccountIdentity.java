package com.swkitchen.member.domain;

import com.swkitchen.common.security.EncryptedLocalDateConverter;
import com.swkitchen.common.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고객 계정만 가진 본인확인 정보와 동의 기록 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountIdentity {

    @Id
    private Long accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String ciHash;

    @Convert(converter = EncryptedLocalDateConverter.class)
    @Column(name = "birth_date_enc", nullable = false, length = 100)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1, columnDefinition = "char(1)")
    private Gender gender;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_enc", nullable = false, length = 100)
    private String phone;

    @Column(nullable = false)
    private LocalDateTime termsAgreedAt;

    @Column(nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(nullable = false)
    private boolean marketingAgreed;

    @Column(nullable = false)
    private LocalDateTime marketingChangedAt;

    public static AccountIdentity create(
            Account account,
            String ciHash,
            LocalDate birthDate,
            Gender gender,
            String phone,
            boolean marketingAgreed,
            LocalDateTime agreedAt) {
        AccountIdentity identity = new AccountIdentity();
        identity.account = account;
        identity.ciHash = ciHash;
        identity.birthDate = birthDate;
        identity.gender = gender;
        identity.phone = phone;
        identity.termsAgreedAt = agreedAt;
        identity.privacyAgreedAt = agreedAt;
        identity.marketingAgreed = marketingAgreed;
        identity.marketingChangedAt = agreedAt;
        return identity;
    }

    public void changeMarketing(boolean agreed, LocalDateTime now) {
        this.marketingAgreed = agreed;
        this.marketingChangedAt = now;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }
}
