package com.swkitchen.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountStatus;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.dto.StaffDto;
import com.swkitchen.member.repository.AccountRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    static final Long ADMIN_ID = 1L;
    static final Long STAFF_ID = 2L;

    @Mock
    AccountRepository accountRepository;

    @Mock
    AuthService authService;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    StaffService staffService;

    Account staff;

    @BeforeEach
    void setUp() {
        staff = Account.create("staff1", "hash", "박담당", Role.STAFF);
        ReflectionTestUtils.setField(staff, "id", STAFF_ID);
        ReflectionTestUtils.setField(staff, "createdAt", LocalDateTime.now());
    }

    @Test
    @DisplayName("만들기: 비밀번호는 해시로 저장한다")
    void create() {
        given(passwordEncoder.encode("1234")).willReturn("new-hash");
        given(accountRepository.saveAndFlush(any())).willAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
            return saved;
        });

        StaffDto.Response response = staffService.create(new StaffDto.CreateRequest("staff2", "이담당", Role.ADMIN, "1234"));

        assertThat(response.userId()).isEqualTo("staff2");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("만들기: 이미 있는 아이디면 USER_ID_DUPLICATE")
    void createDuplicate() {
        given(accountRepository.existsByUserId("staff1")).willReturn(true);

        assertErrorCode(() -> staffService.create(new StaffDto.CreateRequest("staff1", "박담당", Role.STAFF, "1234")),
            ErrorCode.USER_ID_DUPLICATE);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("정지: 상태를 바꾸고 그 계정의 모든 기기를 로그아웃시킨다")
    void suspend() {
        given(accountRepository.findById(STAFF_ID)).willReturn(Optional.of(staff));

        staffService.update(ADMIN_ID, STAFF_ID, new StaffDto.UpdateRequest(null, AccountStatus.SUSPENDED));

        assertThat(staff.isSuspended()).isTrue();
        verify(authService).logoutAll(STAFF_ID);
    }

    @Test
    @DisplayName("역할만 바꾸면 로그아웃시키지 않는다")
    void changeRole() {
        given(accountRepository.findById(STAFF_ID)).willReturn(Optional.of(staff));

        staffService.update(ADMIN_ID, STAFF_ID, new StaffDto.UpdateRequest(Role.ADMIN, null));

        assertThat(staff.getRole()).isEqualTo(Role.ADMIN);
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("자기 계정은 정지하거나 역할을 바꿀 수 없다")
    void selfChange() {
        assertErrorCode(() -> staffService.update(STAFF_ID, STAFF_ID, new StaffDto.UpdateRequest(null, AccountStatus.SUSPENDED)),
            ErrorCode.SELF_CHANGE_NOT_ALLOWED);
        verifyNoInteractions(accountRepository, authService);
    }

    @Test
    @DisplayName("고객 계정 id 는 STAFF_NOT_FOUND")
    void customerIsNotStaff() {
        Account customer = Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER);
        given(accountRepository.findById(3L)).willReturn(Optional.of(customer));

        assertErrorCode(() -> staffService.update(ADMIN_ID, 3L, new StaffDto.UpdateRequest(null, AccountStatus.SUSPENDED)),
            ErrorCode.STAFF_NOT_FOUND);
        assertErrorCode(() -> staffService.resetPassword(3L, new StaffDto.PasswordRequest("1234")),
            ErrorCode.STAFF_NOT_FOUND);
        assertThat(customer.isSuspended()).isFalse();
    }

    @Test
    @DisplayName("비밀번호 새로 정하기: 새 해시로 바꾸고 그 계정의 모든 기기를 로그아웃시킨다")
    void resetPassword() {
        given(accountRepository.findById(STAFF_ID)).willReturn(Optional.of(staff));
        given(passwordEncoder.encode("5678")).willReturn("new-hash");

        staffService.resetPassword(STAFF_ID, new StaffDto.PasswordRequest("5678"));

        assertThat(staff.getPasswordHash()).isEqualTo("new-hash");
        verify(authService).logoutAll(STAFF_ID);
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
