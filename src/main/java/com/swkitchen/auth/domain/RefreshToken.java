package com.swkitchen.auth.domain;

import com.swkitchen.member.domain.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime rotatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static RefreshToken create(Account account, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.account = account;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRotatedBefore(LocalDateTime now, Duration graceTime) {
        return rotatedAt != null && !rotatedAt.plus(graceTime).isAfter(now);
    }

    // 처음 바뀐 시각만 남겨, 그 안에 다시 와도 기한이 늘지 않게 한다
    public void markRotated(LocalDateTime now) {
        if (rotatedAt == null) {
            this.rotatedAt = now;
        }
    }
}
