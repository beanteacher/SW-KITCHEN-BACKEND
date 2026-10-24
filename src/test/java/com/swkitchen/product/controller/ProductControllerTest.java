package com.swkitchen.product.controller;

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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class ProductControllerTest {

    static final String KEY1 = "products/00000000-0000-4000-8000-000000000001.jpg";
    static final String KEY2 = "products/00000000-0000-4000-8000-000000000002.png";
    static final String URL = "http://localhost:4566/sw-kitchen/";

    @Autowired
    MockMvc mvc;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JwtProvider jwtProvider;

    Category fridge;

    @BeforeEach
    void setUp() {
        Category top = categoryRepository.save(Category.create(null, "냉장·냉동", "RF", 1));
        fridge = categoryRepository.save(Category.create(top, "업소용 냉장고", "UR", 1));
    }

    @Test
    @DisplayName("목록: 로그인 없이 열리고, 가장 싼 옵션의 가격과 옵션마다 가격이 다른지, 첫 옵션 이미지, 보이는 수량 합계를 준다")
    void search() throws Exception {
        // 옵션 2개: 첫 옵션 10,000원(이미지 KEY1, 재고 2), 둘째 옵션 정률 50% → 5,000원(재고 1)
        create("""
            { "categoryId": %d, "name": "스텐 작업대", "salesType": "USED", "manufacturer": "대성", "origin": "국산",
              "options": [ { "modelName": "DS-900", "widthMm": 900, "depthMm": 600, "heightMm": 800, "listPrice": 10000,
                             "discountType": "NONE", "stockQty": 2, "mainImageKey": "%s" },
                           { "widthMm": 1200, "depthMm": 600, "heightMm": 800, "listPrice": 10000,
                             "discountType": "RATE", "discountValue": 50, "stockQty": 1, "mainImageKey": "%s" } ] }
            """.formatted(fridge.getId(), KEY1, KEY2));
        create("""
            { "categoryId": %d, "name": "업소용 냉장고", "salesType": "NEW", "manufacturer": "우성", "origin": "국산",
              "options": [ { "widthMm": 1200, "depthMm": 600, "heightMm": 800, "listPrice": 20000,
                             "discountType": "NONE", "mainImageKey": "%s" } ] }
            """.formatted(fridge.getId(), KEY2));

        mvc.perform(get("/api/v1/product").param("salesType", "STOCK", "USED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.content[0].name").value("스텐 작업대"))
            .andExpect(jsonPath("$.data.content[0].salesType").value("USED"))
            .andExpect(jsonPath("$.data.content[0].mainImageUrl").value(URL + KEY1))
            .andExpect(jsonPath("$.data.content[0].price.salePrice").value(5_000))
            .andExpect(jsonPath("$.data.content[0].price.listPrice").value(10_000))
            .andExpect(jsonPath("$.data.content[0].price.discountType").value("RATE"))
            .andExpect(jsonPath("$.data.content[0].price.discountValue").value(50))
            .andExpect(jsonPath("$.data.content[0].price.varies").value(true))
            .andExpect(jsonPath("$.data.content[0].visibleQty").value(3));
        mvc.perform(get("/api/v1/product").param("salesType", "NEW"))
            .andExpect(jsonPath("$.data.content[0].name").value("업소용 냉장고"))
            .andExpect(jsonPath("$.data.content[0].price.varies").value(false))
            .andExpect(jsonPath("$.data.content[0].visibleQty").doesNotExist());
        mvc.perform(get("/api/v1/product").param("keyword", " 작업대 ").param("orderBy", "PRICE_DESC"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("목록: 없는 정렬·재질·판매 구분, 50자 넘는 검색어는 400 이다")
    void searchErrors() throws Exception {
        mvc.perform(get("/api/v1/product").param("orderBy", "CHEAPEST"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("orderBy"))
            .andExpect(jsonPath("$.errors[0].message").value("값이 올바르지 않습니다."));
        mvc.perform(get("/api/v1/product").param("materials", "WOOD"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("materials"));
        mvc.perform(get("/api/v1/product").param("salesType", "RENTAL"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/product").param("keyword", "가".repeat(51)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("keyword"));
    }

    @Test
    @DisplayName("상세: 로그인 없이 열리고, 모델명·재고·잡아 둔 수량 없이 보이는 수량과 이미지 주소를 준다. 없는 제품은 404")
    void detail() throws Exception {
        String created = create("""
            { "categoryId": %d, "name": "스텐 작업대", "salesType": "STOCK", "manufacturer": "대성", "origin": "국산",
              "description": "설명", "materials": ["STAINLESS"],
              "specs": [ { "name": "소비전력", "value": "1300W" } ],
              "imageKeys": ["%s"], "detailImageKeys": ["%s"],
              "options": [ { "modelName": "DS-900", "widthMm": 900, "depthMm": 600, "heightMm": 800, "listPrice": 10000,
                             "discountType": "AMOUNT", "discountValue": 1000, "stockQty": 0,
                             "mainImageKey": "%s", "imageKeys": ["%s"] } ] }
            """.formatted(fridge.getId(), KEY1, KEY2, KEY1, KEY2));
        int id = JsonPath.read(created, "$.data.id");

        mvc.perform(get("/api/v1/product/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("스텐 작업대"))
            .andExpect(jsonPath("$.data.category.name").value("업소용 냉장고"))
            .andExpect(jsonPath("$.data.category.parent.name").value("냉장·냉동"))
            .andExpect(jsonPath("$.data.materials[0].code").value("STAINLESS"))
            .andExpect(jsonPath("$.data.materials[0].name").value("스테인리스"))
            .andExpect(jsonPath("$.data.specs[0].value").value("1300W"))
            .andExpect(jsonPath("$.data.description").value("설명"))
            .andExpect(jsonPath("$.data.imageUrls[0]").value(URL + KEY1))
            .andExpect(jsonPath("$.data.detailImageUrls[0]").value(URL + KEY2))
            .andExpect(jsonPath("$.data.options[0].productCode").value("RFUR-00001-01"))
            .andExpect(jsonPath("$.data.options[0].widthMm").value(900))
            .andExpect(jsonPath("$.data.options[0].salePrice").value(9_000))
            .andExpect(jsonPath("$.data.options[0].visibleQty").value(0))
            .andExpect(jsonPath("$.data.options[0].mainImageUrl").value(URL + KEY1))
            .andExpect(jsonPath("$.data.options[0].imageUrls[0]").value(URL + KEY2))
            .andExpect(jsonPath("$.data.options[0].modelName").doesNotExist())
            .andExpect(jsonPath("$.data.options[0].stockQty").doesNotExist())
            .andExpect(jsonPath("$.data.options[0].reservedQty").doesNotExist());

        // 보이는 수량이 0 이라 목록에는 없다 (DEC-076)
        mvc.perform(get("/api/v1/product"))
            .andExpect(jsonPath("$.data.totalElements").value(0));

        mvc.perform(get("/api/v1/product/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    private String create(String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/product").cookie(new Cookie("access_token", jwtProvider.createAccessToken(1L, Role.STAFF)))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }
}
