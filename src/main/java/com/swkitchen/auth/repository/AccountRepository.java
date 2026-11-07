package com.swkitchen.auth.repository;

import com.swkitchen.auth.domain.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(String userId);

    // 읽고 더해서 쓰면 동시에 틀린 시도가 빠져서 DB 에서 바로 더한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.loginFailCount = a.loginFailCount + 1 WHERE a.id = :id")
    void increaseLoginFailCount(Long id);
}
