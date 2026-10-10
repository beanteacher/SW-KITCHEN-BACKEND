package com.swkitchen.auth.service;

import com.swkitchen.auth.domain.Account;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.auth.repository.AccountRepository;
import com.swkitchen.common.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 계정이 하나도 없을 때 설정값으로 첫 관리자를 만든다. 비밀번호를 SQL 파일에 두지 않기 위해서다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminCreator implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.InitialAdmin admin = properties.initialAdmin();
        if (admin == null || !StringUtils.hasText(admin.userId()) || !StringUtils.hasText(admin.password())) {
            return;
        }
        if (accountRepository.count() > 0) {
            return;
        }
        accountRepository.save(Account.create(admin.userId(), passwordEncoder.encode(admin.password()), Role.ADMIN));
        log.info("첫 관리자 계정을 만들었습니다: {}", admin.userId());
    }
}
