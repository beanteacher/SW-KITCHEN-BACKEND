package com.swkitchen.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

// 기록은 넣기만 한다. 고치거나 지우는 기능은 두지 않는다
public interface PrivacyAuditLogRepository extends JpaRepository<PrivacyAuditLog, Long> {
}
