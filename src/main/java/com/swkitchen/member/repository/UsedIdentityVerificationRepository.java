package com.swkitchen.member.repository;

import com.swkitchen.member.domain.UsedIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedIdentityVerificationRepository extends JpaRepository<UsedIdentityVerification, String> {
}
