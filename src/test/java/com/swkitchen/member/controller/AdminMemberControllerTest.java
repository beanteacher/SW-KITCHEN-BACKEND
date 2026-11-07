package com.swkitchen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.audit.AuditAction;
import com.swkitchen.common.audit.PrivacyAuditLog;
import com.swkitchen.common.audit.PrivacyAuditLogRepository;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.domain.SocialAccount;
import com.swkitchen.member.domain.SocialProvider;
import com.swkitchen.member.repository.AccountRepository;
import com.swkitchen.member.repository.SocialAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminMemberControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    SocialAccountRepository socialAccountRepository;

    @Autowired
    PrivacyAuditLogRepository privacyAuditLogRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    EntityManager em;

    Account staff;
    Account kim;

    @BeforeEach
    void setUp() {
        staff = accountRepository.save(Account.create("staff1", "hash", "박담당", Role.STAFF));
        kim = accountRepository.save(Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER));
        Account lee = accountRepository.save(Account.create(null, null, "이식당", Role.CUSTOMER));
        socialAccountRepository.save(SocialAccount.create(kim, SocialProvider.KAKAO, "3021457788"));
        socialAccountRepository.save(SocialAccount.create(lee, SocialProvider.KAKAO, "3019920451"));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("담당자가 이름으로 찾으면 그 회원이 나오고, 휴대폰 번호·생년월일·마케팅 동의는 없다")
    void searchByName() throws Exception {
        mvc.perform(get("/api/v1/admin/member").param("keyword", " 김주방 ").cookie(accessToken(staff)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].userId").value("kitchen01"))
            .andExpect(jsonPath("$.data.content[0].name").value("김주방"))
            .andExpect(jsonPath("$.data.content[0].linkedProviders[0]").value("KAKAO"))
            .andExpect(jsonPath("$.data.content[0].createdAt").exists())
            .andExpect(jsonPath("$.data.content[0].phone").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].birthDate").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].marketingAgreed").doesNotExist());
    }

    @Test
    @DisplayName("고객만 최신 가입순으로 나오고, 소셜로만 가입한 고객은 아이디가 null")
    void customersOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/member").cookie(accessToken(staff)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.content[0].name").value("이식당"))
            .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
            .andExpect(jsonPath("$.data.content[1].name").value("김주방"));
    }

    @Test
    @DisplayName("검색어가 20자를 넘으면 400 VALIDATION_ERROR")
    void keywordTooLong() throws Exception {
        mvc.perform(get("/api/v1/admin/member").param("keyword", "가".repeat(21)).cookie(accessToken(staff)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("keyword"));
    }

    @Test
    @DisplayName("고객 계정으로 부르면 403, 로그인 안 하면 401")
    void forbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/member").cookie(accessToken(kim)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/member"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("열람 기록: 누가·어디서·무엇을·어떤 검색어로 봤는지 한 줄 남긴다")
    void auditLog() throws Exception {
        mvc.perform(get("/api/v1/admin/member?keyword={keyword}", "김주방")
                .cookie(accessToken(staff))
                .with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    return request;
                }))
            .andExpect(status().isOk());

        assertThat(privacyAuditLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getAccountId()).isEqualTo(staff.getId());
            assertThat(log.getUserId()).isEqualTo("staff1");
            assertThat(log.getIp()).isEqualTo("203.0.113.7");
            assertThat(log.getAction()).isEqualTo(AuditAction.READ);
            assertThat(log.getMethod()).isEqualTo("GET");
            assertThat(log.getPath()).isEqualTo("/api/v1/admin/member");
            assertThat(log.getQuery()).isEqualTo("keyword=김주방");
            assertThat(log.getStatus()).isEqualTo(200);
            assertThat(log.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("열람 기록: 권한이 없어 거부된 요청도 남기고, 로그인 안 한 요청과 다른 경로는 남기지 않는다")
    void auditLogForDenied() throws Exception {
        mvc.perform(get("/api/v1/admin/member").cookie(accessToken(kim))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/member")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/product").cookie(accessToken(staff))).andExpect(status().isOk());

        assertThat(privacyAuditLogRepository.findAll())
            .extracting(PrivacyAuditLog::getUserId, PrivacyAuditLog::getStatus)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("kitchen01", 403));
    }

    private Cookie accessToken(Account account) {
        return new Cookie("access_token", jwtProvider.createAccessToken(account.getId(), account.getRole()));
    }
}
