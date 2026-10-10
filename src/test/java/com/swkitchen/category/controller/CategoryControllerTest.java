package com.swkitchen.category.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.security.JwtProvider;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.repository.ProductRepository;
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
class CategoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JwtProvider jwtProvider;

    Category fridge;
    Category kitchen;
    Category upright;
    Category table;

    @BeforeEach
    void setUp() {
        // 저장 순서와 sortOrder 를 일부러 다르게 둔다
        kitchen = categoryRepository.save(Category.create(null, "조리", "KT", 2));
        fridge = categoryRepository.save(Category.create(null, "냉장·냉동", "RF", 1));
        table = categoryRepository.save(Category.create(fridge.getId(), "테이블 냉장고", "TB", 2));
        upright = categoryRepository.save(Category.create(fridge.getId(), "업소용 냉장고", "UR", 1));
        productRepository.save(product(upright, "RFUR-00001"));
        productRepository.save(product(upright, "RFUR-00002"));
        productRepository.save(product(table, "RFTB-00001"));
    }

    @Test
    @DisplayName("고객용 트리: 로그인 없이 대분류·중분류를 순서대로 주고 제품 수는 주지 않는다")
    void publicTree() throws Exception {
        mvc.perform(get("/api/v1/category"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("냉장·냉동"))
            .andExpect(jsonPath("$.data[0].abbr").value("RF"))
            .andExpect(jsonPath("$.data[0].children[0].name").value("업소용 냉장고"))
            .andExpect(jsonPath("$.data[0].children[1].name").value("테이블 냉장고"))
            .andExpect(jsonPath("$.data[0].productCount").doesNotExist())
            .andExpect(jsonPath("$.data[1].name").value("조리"))
            .andExpect(jsonPath("$.data[1].children").isEmpty());
    }

    @Test
    @DisplayName("관리용 트리: 분류마다 제품 수를 주고 대분류는 아래 중분류의 합이다")
    void adminTree() throws Exception {
        mvc.perform(get("/api/v1/admin/category").cookie(accessToken(Role.STAFF)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].productCount").value(3))
            .andExpect(jsonPath("$.data[0].children[0].productCount").value(2))
            .andExpect(jsonPath("$.data[0].children[1].productCount").value(1))
            .andExpect(jsonPath("$.data[1].productCount").value(0));
    }

    @Test
    @DisplayName("관리용 트리: 로그인 안 하면 401, 제품 관리 권한이 없으면 403")
    void adminTreeNeedsPermission() throws Exception {
        mvc.perform(get("/api/v1/admin/category"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/admin/category").cookie(accessToken(Role.CUSTOMER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private Cookie accessToken(Role role) {
        return new Cookie("access_token", jwtProvider.createAccessToken(1L, role));
    }

    private Product product(Category category, String code) {
        return Product.create(category.getId(), "냉장고", SalesType.NEW, "제조사", "한국", code);
    }
}
