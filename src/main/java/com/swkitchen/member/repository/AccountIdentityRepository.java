package com.swkitchen.member.repository;

import com.swkitchen.member.domain.AccountIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountIdentityRepository extends JpaRepository<AccountIdentity, Long> {

    Optional<AccountIdentity> findByCiHash(String ciHash);
}
