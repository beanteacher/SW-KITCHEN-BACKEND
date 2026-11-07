package com.swkitchen.member.repository;

import com.swkitchen.member.domain.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
}
