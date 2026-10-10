package com.swkitchen.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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

    @Test
    @DisplayName("분류 등록: 대분류는 대분류 중 마지막, 중분류는 형제 중 마지막 순서로 들어간다")
    void create() throws Exception {
        create("{\"name\":\"세척\",\"abbr\":\"WS\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.parentId").doesNotExist())
            .andExpect(jsonPath("$.data.name").value("세척"))
            .andExpect(jsonPath("$.data.abbr").value("WS"))
            .andExpect(jsonPath("$.data.sortOrder").value(3));
        create("{\"parentId\":" + fridge.getId() + ",\"name\":\"쇼케이스\",\"abbr\":\"SC\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.parentId").value(fridge.getId()))
            .andExpect(jsonPath("$.data.sortOrder").value(3));
        create("{\"parentId\":" + kitchen.getId() + ",\"name\":\"화구\",\"abbr\":\"BR\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.sortOrder").value(1));

        assertThat(categoryRepository.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("분류 등록: 이름이 비었거나 20자 초과, 약어가 영문 대문자 2자가 아니면 400 과 칸별 오류")
    void createValidation() throws Exception {
        create("{\"name\":\" \",\"abbr\":\"ws\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field == 'abbr')]").exists());
        create("{\"name\":\"" + "가".repeat(21) + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field == 'abbr')]").exists());
    }

    @Test
    @DisplayName("업무 규칙 오류는 상황별 코드와 문제 칸을 준다: 약어 중복 409, 제품이 연결된 분류 약어 변경 409, 없는 분류 404")
    void businessErrors() throws Exception {
        create("{\"name\":\"냉장\",\"abbr\":\"RF\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_ABBR_DUPLICATE"))
            .andExpect(jsonPath("$.errors[0].field").value("abbr"));
        update(upright, "{\"abbr\":\"UP\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_ABBR_LOCKED"))
            .andExpect(jsonPath("$.errors[0].field").value("abbr"));
        mvc.perform(patch("/api/v1/admin/category/999999").cookie(accessToken(Role.STAFF))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"없음\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("분류 등록: 제품 관리 권한이 없으면 403")
    void createNeedsPermission() throws Exception {
        mvc.perform(post("/api/v1/admin/category").cookie(accessToken(Role.CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"세척\",\"abbr\":\"WS\"}"))
            .andExpect(status().isForbidden());

        assertThat(categoryRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("분류 수정: 보낸 값만 바꾸고 제품이 없는 분류는 약어도 바꾼다")
    void update() throws Exception {
        update(kitchen, "{\"name\":\"조리기기\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("조리기기"))
            .andExpect(jsonPath("$.data.abbr").value("KT"));
        update(kitchen, "{\"abbr\":\"CK\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("조리기기"))
            .andExpect(jsonPath("$.data.abbr").value("CK"));

        Category saved = categoryRepository.findById(kitchen.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("조리기기");
        assertThat(saved.getAbbr()).isEqualTo("CK");
    }

    @Test
    @DisplayName("수정: 빈 이름·틀린 약어는 400 과 칸별 오류")
    void updateValidation() throws Exception {
        update(kitchen, "{\"name\":\" \",\"abbr\":\"c1\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field == 'abbr')]").exists());
    }

    @Test
    @DisplayName("순서 변경: 대분류·중분류 모두 배열 순서대로 1부터 매기고 트리에 그 순서로 나온다")
    void changeOrder() throws Exception {
        changeOrder("{\"categoryIds\":[" + kitchen.getId() + "," + fridge.getId() + "]}")
            .andExpect(status().isOk());
        changeOrder("{\"parentId\":" + fridge.getId() + ",\"categoryIds\":[" + table.getId() + "," + upright.getId() + "]}")
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/category"))
            .andExpect(jsonPath("$.data[0].name").value("조리"))
            .andExpect(jsonPath("$.data[0].sortOrder").value(1))
            .andExpect(jsonPath("$.data[1].name").value("냉장·냉동"))
            .andExpect(jsonPath("$.data[1].sortOrder").value(2))
            .andExpect(jsonPath("$.data[1].children[0].name").value("테이블 냉장고"))
            .andExpect(jsonPath("$.data[1].children[1].name").value("업소용 냉장고"));
    }

    @Test
    @DisplayName("순서 변경: 형제 분류 전체와 다르면 400 CATEGORY_ORDER_MISMATCH, 빈 배열은 400 VALIDATION_ERROR")
    void changeOrderErrors() throws Exception {
        changeOrder("{\"categoryIds\":[" + fridge.getId() + "]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CATEGORY_ORDER_MISMATCH"));
        changeOrder("{\"categoryIds\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("삭제: 빈 분류는 지워져 트리에서 빠지고, 제품이 연결된 중분류는 409 와 이유를 준다")
    void deleteCategory() throws Exception {
        mvc.perform(delete("/api/v1/admin/category/" + kitchen.getId()).cookie(accessToken(Role.STAFF)))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/category/" + upright.getId()).cookie(accessToken(Role.STAFF)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
            .andExpect(jsonPath("$.message").value("하위 분류나 제품이 연결된 분류는 삭제할 수 없습니다."));

        assertThat(categoryRepository.findById(kitchen.getId())).isEmpty();
        assertThat(categoryRepository.findById(upright.getId())).isPresent();
    }

    @Test
    @DisplayName("삭제: 제품 관리 권한이 없으면 403 이고 지워지지 않는다")
    void deleteNeedsPermission() throws Exception {
        mvc.perform(delete("/api/v1/admin/category/" + kitchen.getId()).cookie(accessToken(Role.CUSTOMER)))
            .andExpect(status().isForbidden());

        assertThat(categoryRepository.findById(kitchen.getId())).isPresent();
    }

    private ResultActions changeOrder(String body) throws Exception {
        return mvc.perform(put("/api/v1/admin/category/order").cookie(accessToken(Role.STAFF))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(Category category, String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/category/" + category.getId()).cookie(accessToken(Role.STAFF))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/category").cookie(accessToken(Role.STAFF))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Cookie accessToken(Role role) {
        return new Cookie("access_token", jwtProvider.createAccessToken(1L, role));
    }

    private Product product(Category category, String code) {
        return Product.create(category.getId(), "냉장고", SalesType.NEW, "제조사", "한국", code);
    }
}
