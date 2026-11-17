package com.swkitchen.member.repository;

import com.swkitchen.member.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountRepositoryCustom {

    void increaseLoginFailCount(Long accountId);

    Page<Account> searchCustomers(String keyword, Pageable pageable);
}
