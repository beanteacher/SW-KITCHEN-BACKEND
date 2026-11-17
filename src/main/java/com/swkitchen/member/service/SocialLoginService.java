package com.swkitchen.member.service;

import com.swkitchen.auth.dto.AuthDto;
import com.swkitchen.auth.service.AuthService;
import com.swkitchen.common.dto.ErrorResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.common.oauth.KakaoLoginClient;
import com.swkitchen.common.oauth.NaverLoginClient;
import com.swkitchen.common.oauth.OAuthProperties;
import com.swkitchen.common.oauth.SocialLoginClient;
import com.swkitchen.common.security.CryptoUtil;
import com.swkitchen.common.security.SealedCookieCodec;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.AccountIdentity;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import com.swkitchen.member.domain.VerificationPurpose;
import com.swkitchen.member.dto.IdentityVerificationDto;
import com.swkitchen.member.dto.SignupDto;
import com.swkitchen.member.dto.SocialLoginDto;
import com.swkitchen.member.repository.AccountIdentityRepository;
import com.swkitchen.member.repository.SocialAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SocialLoginService {

    public static final Duration STATE_TIME = Duration.ofMinutes(10);
    public static final Duration SIGNUP_TIME = Duration.ofMinutes(10);
    public static final Duration REAUTH_TIME = Duration.ofMinutes(5);
    public static final String SIGNUP_PAGE = "/signup/social";
    private static final String LOGIN_ERROR_PAGE = "/login?error=social";
    private static final int STATE_BYTES = 24;

    private final KakaoLoginClient kakaoLoginClient;
    private final NaverLoginClient naverLoginClient;
    private final OAuthProperties properties;
    private final SealedCookieCodec sealedCookieCodec;
    private final SocialAccountRepository socialAccountRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final IdentityVerificationService identityVerificationService;
    private final SignupService signupService;
    private final AuthService authService;

    /** 카카오·네이버 로그인 화면 주소와, 돌아왔을 때 맞춰 볼 oauth_state 쿠키 값 */
    public Start start(SocialProvider provider, String returnUrl, boolean reauth) {
        String state = CryptoUtil.randomToken(STATE_BYTES);
        String cookie = sealedCookieCodec.seal(new SocialLoginDto.OAuthState(
            state, provider, safeReturnUrl(returnUrl), reauth, Instant.now().plus(STATE_TIME)));

        return new Start(clientOf(provider).authorizeUrl(state, redirectUri(provider)), cookie);
    }

    /**
     * 연결된 계정이 있으면 로그인, 없으면 가입·연결 화면으로 보낸다. reauth 면 로그인한 계정에 연결된 소셜인지만 본다.
     * 실패는 모두 로그인 화면(reauth 면 돌아갈 화면)에 오류 표시로 보낸다
     */
    public SocialLoginDto.CallbackResult callback(
            SocialProvider provider,
            String code,
            String state,
            String stateCookie,
            Long loggedInAccountId) {
        Optional<SocialLoginDto.OAuthState> saved = sealedCookieCodec.open(stateCookie, SocialLoginDto.OAuthState.class)
            .filter(s -> s.expiresAt().isAfter(Instant.now()))
            .filter(s -> s.provider() == provider)
            .filter(s -> state != null && CryptoUtil.constantTimeEquals(s.state(), state));
        if (saved.isEmpty() || code == null) {
            return redirect(LOGIN_ERROR_PAGE);
        }
        SocialLoginDto.OAuthState oauthState = saved.get();
        Optional<String> providerUserId = clientOf(provider).fetchUserId(code, state, redirectUri(provider));
        if (providerUserId.isEmpty()) {
            return redirect(oauthState.reauth() ? withError(oauthState.returnUrl(), "reauth") : LOGIN_ERROR_PAGE);
        }
        Optional<Account> linked = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId.get())
            .map(SocialAccount::getAccount);

        if (oauthState.reauth()) {
            boolean same = loggedInAccountId != null && linked.map(a -> a.getId().equals(loggedInAccountId)).orElse(false);
            if (!same) {
                return redirect(withError(oauthState.returnUrl(), "reauth"));
            }
            String reauth = sealedCookieCodec.seal(
                new SocialLoginDto.SocialReauth(loggedInAccountId, Instant.now().plus(REAUTH_TIME)));
            return new SocialLoginDto.CallbackResult(oauthState.returnUrl(), null, null, reauth);
        }
        if (linked.isPresent()) {
            return new SocialLoginDto.CallbackResult(oauthState.returnUrl(), authService.issueLogin(linked.get()), null, null);
        }
        String signup = sealedCookieCodec.seal(
            new SocialLoginDto.SocialSignup(provider, providerUserId.get(), Instant.now().plus(SIGNUP_TIME)));
        // 쿠키는 화면이 못 읽으므로, 시간이 지났을 때 어느 소셜로 다시 시작할지 주소로 알려 준다
        return new SocialLoginDto.CallbackResult(SIGNUP_PAGE + "?provider=" + provider.name().toLowerCase(), null, signup, null);
    }

    /** 소셜로 새로 가입한다. 아이디·비밀번호가 없는 계정이다 */
    public AuthDto.LoginResult signup(String socialSignupCookie, String verifiedIdentityCookie, SignupDto.SocialSignupRequest request) {
        SocialLoginDto.SocialSignup social = requireSocialSignup(socialSignupCookie);
        IdentityVerificationDto.VerifiedIdentity verified =
            identityVerificationService.require(verifiedIdentityCookie, VerificationPurpose.SIGNUP);
        Account account = signupService.createCustomer(verified, null, null, request.agreements());
        saveSocialAccount(account, social);

        return authService.issueLogin(account);
    }

    /** 같은 사람(CI)의 기존 계정에 이 소셜을 잇고 로그인한다. 기존 계정이 아이디 가입이면 비밀번호가 맞아야 한다 */
    public AuthDto.LoginResult link(String socialSignupCookie, String verifiedIdentityCookie, SocialLoginDto.LinkRequest request) {
        SocialLoginDto.SocialSignup social = requireSocialSignup(socialSignupCookie);
        IdentityVerificationDto.VerifiedIdentity verified =
            identityVerificationService.require(verifiedIdentityCookie, VerificationPurpose.SIGNUP);
        Account account = accountIdentityRepository.findByCiHash(verified.ciHash())
            .map(AccountIdentity::getAccount)
            .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (account.getPasswordHash() != null
                && (request.password() == null || !authService.matchesPassword(account, request.password()))) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH,
                List.of(new ErrorResponse.FieldError("password", ErrorCode.PASSWORD_MISMATCH.getMessage())));
        }
        if (socialAccountRepository.existsByAccountIdAndProvider(account.getId(), social.provider())) {
            throw new AppException(ErrorCode.SOCIAL_ALREADY_LINKED);
        }
        saveSocialAccount(account, social);

        return authService.issueLogin(account);
    }

    /** 탈퇴 전에 소셜 로그인을 다시 했는지. 쿠키가 이 계정 것이고 5분 안이어야 한다 */
    @Transactional(readOnly = true)
    public boolean isReauthenticated(String reauthCookie, Long accountId) {
        return sealedCookieCodec.open(reauthCookie, SocialLoginDto.SocialReauth.class)
            .filter(r -> r.expiresAt().isAfter(Instant.now()))
            .map(r -> r.accountId().equals(accountId))
            .orElse(false);
    }

    private SocialLoginDto.SocialSignup requireSocialSignup(String cookie) {
        return sealedCookieCodec.open(cookie, SocialLoginDto.SocialSignup.class)
            .filter(s -> s.expiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new AppException(ErrorCode.SOCIAL_SIGNUP_EXPIRED));
    }

    private void saveSocialAccount(Account account, SocialLoginDto.SocialSignup social) {
        try {
            socialAccountRepository.saveAndFlush(SocialAccount.create(account, social.provider(), social.providerUserId()));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.SOCIAL_ALREADY_LINKED);
        }
    }

    private SocialLoginClient clientOf(SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> kakaoLoginClient;
            case NAVER -> naverLoginClient;
        };
    }

    private String redirectUri(SocialProvider provider) {
        return properties.redirectBaseUrl() + "/api/v1/auth/oauth/" + provider.name().toLowerCase() + "/callback";
    }

    // 다른 사이트로 보내는 데 쓰이지 않게 우리 사이트 경로만 받는다 (//evil.com, /\evil.com 막기)
    static String safeReturnUrl(String returnUrl) {
        if (returnUrl == null || !returnUrl.startsWith("/") || returnUrl.startsWith("//") || returnUrl.contains("\\")) {
            return "/";
        }
        return returnUrl;
    }

    private static String withError(String url, String error) {
        return url + (url.contains("?") ? "&" : "?") + "error=" + error;
    }

    private static SocialLoginDto.CallbackResult redirect(String url) {
        return new SocialLoginDto.CallbackResult(url, null, null, null);
    }

    public record Start(String authorizeUrl, String stateCookie) {
    }
}
