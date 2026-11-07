package com.swkitchen.member.repository;

import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    boolean existsByAccountIdAndProvider(Long accountId, SocialProvider provider);

    void deleteByAccountId(Long accountId);
}
