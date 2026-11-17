package com.swkitchen.common.audit;

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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacyAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 계정이 지워져도 기록은 남아야 해서 Account 와 잇지 않고 번호·아이디를 복사해 둔다
    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 20)
    private String userId;

    @Column(nullable = false, length = 45)
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditAction action;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(length = 500)
    private String query;

    @Column(nullable = false, columnDefinition = "smallint")
    private int status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PrivacyAuditLog create(
            Long accountId,
            String userId,
            String ip,
            AuditAction action,
            String method,
            String path,
            String query,
            int status) {
        PrivacyAuditLog log = new PrivacyAuditLog();
        log.accountId = accountId;
        log.userId = userId;
        log.ip = ip;
        log.action = action;
        log.method = method;
        log.path = path;
        log.query = query;
        log.status = status;
        return log;
    }
}
