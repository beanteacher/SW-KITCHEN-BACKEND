package com.swkitchen.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    private static final int CAPTCHA_FAIL_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String userId;

    // 소셜로만 가입한 고객은 아이디·비밀번호가 없다
    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    @Column(nullable = false)
    private int loginFailCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Account create(String userId, String passwordHash, String name, Role role) {
        Account account = new Account();
        account.userId = userId;
        account.passwordHash = passwordHash;
        account.name = name;
        account.role = role;
        account.status = AccountStatus.ACTIVE;
        return account;
    }

    public boolean isCaptchaRequired() {
        return loginFailCount >= CAPTCHA_FAIL_COUNT;
    }

    public boolean isSuspended() {
        return status == AccountStatus.SUSPENDED;
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
