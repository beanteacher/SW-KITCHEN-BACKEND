package com.swkitchen.member.repository;

import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

    Optional<Account> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<Account> findByRoleInOrderByIdAsc(Collection<Role> roles);
}
