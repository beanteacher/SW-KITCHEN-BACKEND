package com.swkitchen.product.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swkitchen.TestcontainersConfig;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.security.JwtProvider;
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
class AdminProductControllerTest {

    static final String KEY1 = "products/00000000-0000-4000-8000-000000000001.jpg";
    static final String KEY2 = "products/00000000-0000-4000-8000-000000000002.png";

    @Autowired
    MockMvc mvc;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JwtProvider jwtProvider;

    Category top;
    Category fridge;

    @BeforeEach
    void setUp() {
        top = categoryRepository.save(Category.create(null, "냉장·냉동", "RF", 1));
        fridge = categoryRepository.save(Category.create(top, "업소용 냉장고", "UR", 1));
    }

    @Test
    @DisplayName("등록: 201 과 상품 코드·판매가·이미지 주소가 담긴 전체 데이터를 주고, 같은 데이터를 다시 조회할 수 있다")
    void create() throws Exception {
        String body = """
            { "categoryId": %d, "name": "업소용 냉장고 45박스", "salesType": "STOCK",
              "manufacturer": "우성", "origin": "국산", "description": "설명",
              "materials": ["STAINLESS", "PLASTIC"],
              "specs": [ { "name": "냉동 구성", "value": "1/4냉동" } ],
              "imageKeys": ["%s"], "detailImageKeys": [],
              "options": [ { "modelName": "WSM-1243RF", "widthMm": 1260, "depthMm": 800, "heightMm": 1900,
                             "listPrice": 2000000, "discountType": "RATE", "discountValue": 17, "stockQty": 3,
                             "mainImageKey": "%s", "imageKeys": ["%s"] } ] }
            """.formatted(fridge.getId(), KEY1, KEY2, KEY1);

        String response = create(body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.category.name").value("업소용 냉장고"))
            .andExpect(jsonPath("$.data.category.parent.name").value("냉장·냉동"))
            .andExpect(jsonPath("$.data.materials[*].code", containsInAnyOrder("STAINLESS", "PLASTIC")))
            .andExpect(jsonPath("$.data.materials[?(@.code == 'STAINLESS')].name").value("스테인리스"))
            .andExpect(jsonPath("$.data.specs[0].name").value("냉동 구성"))
            .andExpect(jsonPath("$.data.images[0].key").value(KEY1))
            .andExpect(jsonPath("$.data.images[0].url").value("http://localhost:9000/sw-kitchen/" + KEY1))
            .andExpect(jsonPath("$.data.options[0].productCode").value("RFUR-00001-01"))
            .andExpect(jsonPath("$.data.options[0].modelName").value("WSM-1243RF"))
            .andExpect(jsonPath("$.data.options[0].salePrice").value(1_660_000))
            .andExpect(jsonPath("$.data.options[0].stockQty").value(3))
            .andExpect(jsonPath("$.data.options[0].reservedQty").value(0))
            .andExpect(jsonPath("$.data.options[0].mainImage.key").value(KEY2))
            .andExpect(jsonPath("$.data.options[0].images[0].key").value(KEY1))
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.data.id");

        mvc.perform(get("/api/v1/admin/product/" + id).cookie(accessToken(Role.STAFF)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("업소용 냉장고 45박스"))
            .andExpect(jsonPath("$.data.options[0].productCode").value("RFUR-00001-01"));
    }

    @Test
    @DisplayName("입력 오류: 400 과 함께 옵션·칸 경로로 항목별 오류를 한 번에 준다")
    void validationErrors() throws Exception {
        String body = """
            { "categoryId": %d, "name": "", "salesType": "USED", "manufacturer": "우성", "origin": "국산",
              "imageKeys": ["products/abc.gif"],
              "options": [
                { "widthMm": 1200, "depthMm": 600, "heightMm": 800, "listPrice": 10000, "discountType": "NONE",
                  "stockQty": 1, "mainImageKey": "%s" },
                { "widthMm": 1200, "depthMm": 600, "heightMm": 800, "listPrice": 10000, "discountType": "AMOUNT",
                  "discountValue": 10000, "mainImageKey": "%s" } ] }
            """.formatted(fridge.getId(), KEY1, KEY1);

        create(body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "imageKeys[0]",
                "options[1].discountValue", "options[1].stockQty", "options[1].widthMm")));
    }

    @Test
    @DisplayName("없는 재질 값은 400 이다")
    void unknownMaterial() throws Exception {
        create("""
            { "categoryId": %d, "name": "냉장고", "salesType": "NEW", "manufacturer": "우성", "origin": "국산",
              "materials": ["WOOD"], "options": [] }
            """.formatted(fridge.getId()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("업무 오류: 대분류에 넣으면 400, 없는 제품 조회는 404")
    void businessErrors() throws Exception {
        create("""
            { "categoryId": %d, "name": "냉장고", "salesType": "NEW", "manufacturer": "우성", "origin": "국산",
              "options": [ { "widthMm": 1200, "depthMm": 600, "heightMm": 800, "listPrice": 10000,
                             "discountType": "NONE", "mainImageKey": "%s" } ] }
            """.formatted(top.getId(), KEY1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CATEGORY_NOT_MIDDLE"))
            .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        mvc.perform(get("/api/v1/admin/product/999999").cookie(accessToken(Role.STAFF)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("권한: 로그인 안 하면 401, 고객은 403, 관리자는 등록할 수 있다")
    void permission() throws Exception {
        String body = """
            { "categoryId": %d, "name": "냉장고", "salesType": "NEW", "manufacturer": "우성", "origin": "국산",
              "options": [ { "widthMm": 1200, "depthMm": 600, "heightMm": 800, "listPrice": 10000,
                             "discountType": "NONE", "mainImageKey": "%s" } ] }
            """.formatted(fridge.getId(), KEY1);

        mvc.perform(post("/api/v1/admin/product").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/product").cookie(accessToken(Role.CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/product/1").cookie(accessToken(Role.CUSTOMER)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/product").cookie(accessToken(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/product").cookie(accessToken(Role.STAFF))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Cookie accessToken(Role role) {
        return new Cookie("access_token", jwtProvider.createAccessToken(1L, role));
    }
}
