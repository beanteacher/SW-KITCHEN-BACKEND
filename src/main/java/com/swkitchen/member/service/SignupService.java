package com.swkitchen.member.service;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.domain.VerificationPurpose;
import com.swkitchen.member.dto.IdentityVerificationDto;
import com.swkitchen.member.dto.SignupDto;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.AccountRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SignupService {

    private final AccountRepository accountRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final IdentityVerificationService identityVerificationService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public SignupDto.UserIdCheckResponse checkUserId(SignupDto.UserIdCheckRequest request) {
        return new SignupDto.UserIdCheckResponse(!accountRepository.existsByUserId(request.userId()));
    }

    /** 가입하고 바로 로그인한다 */
    public AuthDto.LoginResult signup(String verifiedIdentityCookie, SignupDto.SignupRequest request) {
        IdentityVerificationDto.VerifiedIdentity verified =
            identityVerificationService.require(verifiedIdentityCookie, VerificationPurpose.SIGNUP);
        if (accountRepository.existsByUserId(request.userId())) {
            throw new AppException(ErrorCode.USER_ID_DUPLICATE);
        }
        Account account = createCustomer(
            verified,
            request.userId(),
            passwordEncoder.encode(request.password()),
            request.agreements());

        return authService.issueLogin(account);
    }

    /** 아이디·비밀번호 없는 고객 계정을 만든다. 소셜 가입이 쓴다 */
    public Account createCustomer(
            IdentityVerificationDto.VerifiedIdentity verified,
            String userId,
            String passwordHash,
            SignupDto.Agreements agreements) {
        if (accountIdentityRepository.findByCiHash(verified.ciHash()).isPresent()) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        Account account = Account.create(userId, passwordHash, verified.name(), Role.CUSTOMER);
        AccountIdentity identity = AccountIdentity.create(
            account,
            verified.ciHash(),
            verified.birthDate(),
            verified.gender(),
            verified.phone(),
            agreements.marketing(),
            LocalDateTime.now());
        try {
            accountRepository.save(account);
            accountIdentityRepository.saveAndFlush(identity);
        } catch (DataIntegrityViolationException e) {
            // 위에서 봤지만 동시에 두 번 가입하면 DB 가 막는다. 어느 쪽이 겹쳤는지 다시 본다
            if (userId != null && accountRepository.existsByUserId(userId)) {
                throw new AppException(ErrorCode.USER_ID_DUPLICATE);
            }
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        return account;
    }
}
