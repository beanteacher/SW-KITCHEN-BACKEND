package com.swkitchen.member.service;

import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.dto.ErrorResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.VerificationPurpose;
import com.swkitchen.member.dto.IdentityVerificationDto;
import com.swkitchen.member.dto.MyPageDto;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.AccountRepository;
import com.swkitchen.member.repository.SocialAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MyPageService {

    private final AccountRepository accountRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final IdentityVerificationService identityVerificationService;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public MyPageDto.MeResponse me(Long accountId) {
        AccountIdentity identity = findIdentity(accountId);

        return MyPageDto.MeResponse.of(identity.getAccount(), identity);
    }

    public void changeMarketing(Long accountId, MyPageDto.MarketingRequest request) {
        findIdentity(accountId).changeMarketing(request.agreed(), LocalDateTime.now());
    }

    /** 다시 한 본인확인이 처음 가입한 사람과 같을 때만 번호를 바꾼다 */
    public void changePhone(Long accountId, String verifiedIdentityCookie) {
        IdentityVerificationDto.VerifiedIdentity verified =
            identityVerificationService.require(verifiedIdentityCookie, VerificationPurpose.PHONE_CHANGE);
        AccountIdentity identity = findIdentity(accountId);
        if (!identity.getCiHash().equals(verified.ciHash())) {
            throw new AppException(ErrorCode.IDENTITY_MISMATCH);
        }

        identity.changePhone(verified.phone());
    }

    /**
     * 회원 정보를 바로 지우고 모든 기기를 로그아웃시킨다. 되돌릴 수 없다.
     * 같은 사람이 다시 가입하면 옛 정보 없이 새 계정이다
     */
    public void withdraw(Long accountId, MyPageDto.WithdrawRequest request, boolean socialReauthenticated) {
        Account account = findIdentity(accountId).getAccount();
        if (account.getPasswordHash() != null) {
            if (request.password() == null || !authService.matchesPassword(account, request.password())) {
                throw new AppException(ErrorCode.PASSWORD_MISMATCH,
                    List.of(new ErrorResponse.FieldError("password", ErrorCode.PASSWORD_MISMATCH.getMessage())));
            }
        } else if (!socialReauthenticated) {
            throw new AppException(ErrorCode.REAUTH_REQUIRED);
        }

        authService.logoutAll(accountId);
        socialAccountRepository.deleteByAccountId(accountId);
        accountIdentityRepository.deleteById(accountId);
        accountRepository.delete(account);
    }

    // 마이페이지는 고객만 쓴다. 고객인데 본인확인 정보가 없을 수는 없다
    private AccountIdentity findIdentity(Long accountId) {
        return accountIdentityRepository.findById(accountId)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }
}
