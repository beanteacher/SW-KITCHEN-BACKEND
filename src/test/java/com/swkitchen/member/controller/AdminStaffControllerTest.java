package com.swkitchen.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Role;
import com.swkitchen.member.repository.AccountRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminStaffControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtProvider jwtProvider;

    Account admin;
    Account staff;

    // IP 시도 제한은 테스트끼리 나눠 쓰므로 테스트마다 다른 IP 로 보낸다
    String ip;

    @BeforeEach
    void setUp() {
        admin = accountRepository.save(Account.create("boss1", passwordEncoder.encode("1234"), "최관리", Role.ADMIN));
        staff = accountRepository.save(Account.create("staff1", passwordEncoder.encode("1234"), "박담당", Role.STAFF));
        accountRepository.save(Account.create("kitchen01", "hash", "김주방", Role.CUSTOMER));
        ip = "test-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("목록: 담당자·관리자 계정만 준다")
    void getAll() throws Exception {
        mvc.perform(get("/api/v1/admin/staff").cookie(accessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.userId == 'boss1')].role").value("ADMIN"))
            .andExpect(jsonPath("$.data[?(@.userId == 'staff1')].status").value("ACTIVE"))
            .andExpect(jsonPath("$.data[?(@.userId == 'kitchen01')]").isEmpty());
    }

    @Test
    @DisplayName("만들기: 201 이고, 만든 아이디·첫 비밀번호로 로그인된다")
    void create() throws Exception {
        create("""
            { "userId": "staff2", "name": "이담당", "role": "STAFF", "password": "1" }
            """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value("staff2"))
            .andExpect(jsonPath("$.data.name").value("이담당"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.password").doesNotExist());

        login("staff2", "1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("만들기: 이미 있는 아이디는 409 USER_ID_DUPLICATE, 고객 역할·틀린 아이디 형식은 400")
    void createInvalid() throws Exception {
        create("""
            { "userId": "kitchen01", "name": "김", "role": "STAFF", "password": "1" }
            """)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USER_ID_DUPLICATE"));
        create("""
            { "userId": "staff3", "name": "김", "role": "CUSTOMER", "password": "1" }
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("staffRole"));
        create("""
            { "userId": "Staff3", "name": "김", "role": "STAFF", "password": "1" }
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    @Test
    @DisplayName("정지: 이미 로그인한 기기도 다음 재발급 때 401 이고, 다시 로그인하면 403 ACCOUNT_SUSPENDED")
    void suspend() throws Exception {
        Cookie refresh = login("staff1", "1234").andReturn().getResponse().getCookie("refresh_token");

        update(staff.getId(), """
            { "status": "SUSPENDED" }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        login("staff1", "1234")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("정지 해제·역할 바꾸기: 보낸 값만 바뀐다")
    void updateRole() throws Exception {
        update(staff.getId(), """
            { "role": "ADMIN" }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("자기 계정을 정지하면 409 SELF_CHANGE_NOT_ALLOWED, 고객 계정 id 는 404 STAFF_NOT_FOUND")
    void updateNotAllowed() throws Exception {
        update(admin.getId(), """
            { "status": "SUSPENDED" }
            """)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SELF_CHANGE_NOT_ALLOWED"));
        Long customerId = accountRepository.findByUserId("kitchen01").orElseThrow().getId();
        update(customerId, """
            { "status": "SUSPENDED" }
            """)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
    }

    @Test
    @DisplayName("비밀번호 새로 정하기: 새 비밀번호로만 로그인되고 이미 로그인한 기기는 로그아웃된다")
    void resetPassword() throws Exception {
        Cookie refresh = login("staff1", "1234").andReturn().getResponse().getCookie("refresh_token");

        mvc.perform(put("/api/v1/admin/staff/" + staff.getId() + "/password")
                .cookie(accessToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"5678\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        login("staff1", "1234").andExpect(status().isUnauthorized());
        login("staff1", "5678").andExpect(status().isOk());
    }

    @Test
    @DisplayName("담당자 계정으로 직원 API 를 부르면 403")
    void staffForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/staff").cookie(accessToken(staff)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("권한을 정하지 않은 백오피스 경로는 관리자도 403")
    void unknownAdminPath() throws Exception {
        mvc.perform(get("/api/v1/admin/unknown").cookie(accessToken(admin)))
            .andExpect(status().isForbidden());
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/staff")
            .cookie(accessToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions update(Long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/staff/" + id)
            .cookie(accessToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions login(String userId, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"));
    }

    private Cookie accessToken(Account account) {
        return new Cookie("access_token", jwtProvider.createAccessToken(account.getId(), account.getRole()));
    }
}
