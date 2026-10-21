package com.swkitchen;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SwKitchenApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("실제 MySQL 에 붙어 Flyway·JPA 검증까지 마치고 앱이 뜬다")
    void contextLoads() {
    }

    @Test
    @DisplayName("개발 환경에서는 로그인 없이 API 문서를 볼 수 있다")
    void apiDocs() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/admin/product/{id}']").exists());
        mvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }
}
