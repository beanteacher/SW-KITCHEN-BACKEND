package com.swkitchen.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsedIdentityVerification implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String verificationId;

    @Column(nullable = false)
    private LocalDateTime usedAt;

    public static UsedIdentityVerification create(String verificationId, LocalDateTime usedAt) {
        UsedIdentityVerification used = new UsedIdentityVerification();
        used.verificationId = verificationId;
        used.usedAt = usedAt;
        return used;
    }

    @Override
    public String getId() {
        return verificationId;
    }

    // 항상 새 행으로 넣는다. 같은 번호가 있으면 기본 키에 걸려 실패해야 한다 (덮어쓰면 재사용을 못 막는다)
    @Override
    public boolean isNew() {
        return true;
    }
}
