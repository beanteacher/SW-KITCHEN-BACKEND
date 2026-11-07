package com.swkitchen.member.service;

import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountStatus;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.dto.StaffDto;
import com.swkitchen.member.repository.AccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StaffService {

    private static final List<Role> STAFF_ROLES = List.of(Role.STAFF, Role.ADMIN);

    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StaffDto.Response> getAll() {
        return accountRepository.findByRoleInOrderByIdAsc(STAFF_ROLES).stream()
            .map(StaffDto.Response::from)
            .toList();
    }

    public StaffDto.Response create(StaffDto.CreateRequest request) {
        if (accountRepository.existsByUserId(request.userId())) {
            throw new AppException(ErrorCode.USER_ID_DUPLICATE);
        }
        Account account = Account.create(
            request.userId(),
            passwordEncoder.encode(request.password()),
            request.name(),
            request.role());

        return StaffDto.Response.from(accountRepository.saveAndFlush(account));
    }

    public StaffDto.Response update(Long adminId, Long id, StaffDto.UpdateRequest request) {
        if (adminId.equals(id)) {
            throw new AppException(ErrorCode.SELF_CHANGE_NOT_ALLOWED);
        }
        Account account = findStaff(id);
        if (request.role() != null) {
            account.changeRole(request.role());
        }
        if (request.status() != null) {
            account.changeStatus(request.status());
            if (request.status() == AccountStatus.SUSPENDED) {
                authService.logoutAll(id);
            }
        }

        return StaffDto.Response.from(account);
    }

    public void resetPassword(Long id, StaffDto.PasswordRequest request) {
        Account account = findStaff(id);
        account.changePassword(passwordEncoder.encode(request.password()));
        authService.logoutAll(id);
    }

    // 고객 계정 id 도 없는 직원으로 본다
    private Account findStaff(Long id) {
        return accountRepository.findById(id)
            .filter(account -> account.getRole() != Role.CUSTOMER)
            .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
    }
}
