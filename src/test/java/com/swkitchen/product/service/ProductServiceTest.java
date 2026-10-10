package com.swkitchen.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.TestcontainersConfig;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.Material;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.dto.ProductDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 롤백을 확인하려고 테스트에 트랜잭션을 걸지 않는다. 테스트마다 테이블을 비운다 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ProductServiceTest {

    static final String KEY1 = "products/00000000-0000-4000-8000-000000000001.jpg";
    static final String KEY2 = "products/00000000-0000-4000-8000-000000000002.png";
    static final String KEY3 = "products/00000000-0000-4000-8000-000000000003.webp";

    @Autowired
    ProductService productService;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JdbcTemplate jdbc;

    Category top;
    Category fridge;

    @BeforeEach
    void setUp() {
        top = categoryRepository.save(Category.create(null, "냉장·냉동", "RF", 1));
        fridge = categoryRepository.save(Category.create(top, "업소용 냉장고", "UR", 1));
    }

    @AfterEach
    void cleanUp() {
        for (String table : List.of("option_image", "product_image", "product_detail_image", "product_spec",
                "product_material", "product_option", "product", "product_code_sequence")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM category WHERE parent_id IS NOT NULL");
        jdbc.update("DELETE FROM category");
    }

    @Test
    @DisplayName("제품·옵션·사양·재질·이미지를 한 번에 저장하고 상품 코드를 붙인다")
    void create() {
        ProductDto.SaveRequest request = new ProductDto.SaveRequest(fridge.getId(), "업소용 냉장고 45박스", SalesType.NEW,
            "우성", "국산", "설명", List.of(Material.STAINLESS, Material.PLASTIC),
            List.of(new ProductDto.SpecRequest("냉동 구성", "1/4냉동"), new ProductDto.SpecRequest("소비전력", "1300W")),
            List.of(KEY2), List.of(KEY3, KEY1),
            List.of(option(1260, DiscountType.RATE, 17, 5, List.of(KEY2, KEY3)),
                option(1500, DiscountType.NONE, 3_000, null, List.of())));

        Long id = productService.create(request).id();

        assertThat(jdbc.queryForMap("SELECT name, description, code_prefix, last_option_no FROM product WHERE id = ?", id))
            .containsEntry("name", "업소용 냉장고 45박스").containsEntry("description", "설명")
            .containsEntry("code_prefix", "RFUR-00001").containsEntry("last_option_no", 2);
        assertThat(jdbc.queryForList("SELECT material FROM product_material WHERE product_id = ?", String.class, id))
            .containsExactlyInAnyOrder("STAINLESS", "PLASTIC");

        List<Map<String, Object>> options = jdbc.queryForList(
            "SELECT id, product_code, sale_price, discount_value, stock_qty FROM product_option WHERE product_id = ? ORDER BY option_no",
            id);
        assertThat(options).extracting(o -> o.get("product_code")).containsExactly("RFUR-00001-01", "RFUR-00001-02");
        // 정률 17% → 100원 미만 버림. 할인 없음이면 보낸 값을 버린다. 신제품은 재고를 버린다
        assertThat(options).extracting(o -> o.get("sale_price")).containsExactly(1_660_000, 2_000_000);
        assertThat(options).extracting(o -> o.get("discount_value")).containsExactly(17, null);
        assertThat(options).extracting(o -> o.get("stock_qty")).containsExactly(null, null);

        assertThat(jdbc.queryForList("SELECT name FROM product_spec WHERE product_id = ? ORDER BY sort_order",
            String.class, id)).containsExactly("냉동 구성", "소비전력");
        assertThat(jdbc.queryForList("SELECT image_key FROM product_image WHERE product_id = ? ORDER BY sort_order",
            String.class, id)).containsExactly(KEY2);
        assertThat(jdbc.queryForList(
            "SELECT image_key FROM product_detail_image WHERE product_id = ? ORDER BY sort_order", String.class, id))
            .containsExactly(KEY3, KEY1);
        assertThat(jdbc.queryForList(
            "SELECT image_key FROM option_image WHERE product_option_id = ? ORDER BY sort_order", String.class,
            options.get(0).get("id"))).containsExactly(KEY2, KEY3);
    }

    @Test
    @DisplayName("같은 중분류의 다음 제품은 다음 번호를 받는다. 재고품은 재고를 저장한다")
    void nextNumber() {
        productService.create(request(SalesType.NEW, List.of(option(1200, DiscountType.NONE, null, null, List.of()))));
        Long id = productService.create(
            request(SalesType.STOCK, List.of(option(1200, DiscountType.NONE, null, 3, List.of())))).id();

        assertThat(jdbc.queryForMap("SELECT product_code, stock_qty FROM product_option WHERE product_id = ?", id))
            .containsEntry("product_code", "RFUR-00002-01").containsEntry("stock_qty", 3);
    }

    @Test
    @DisplayName("없는 분류·대분류에는 제품을 넣을 수 없다")
    void category() {
        List<ProductDto.OptionRequest> options = List.of(option(1200, DiscountType.NONE, null, null, List.of()));

        assertThatThrownBy(() -> productService.create(request(999_999L, SalesType.NEW, options)))
            .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
        assertThatThrownBy(() -> productService.create(request(top.getId(), SalesType.NEW, options)))
            .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_MIDDLE);
    }

    @Test
    @DisplayName("옵션 3개 중 마지막만 DB 규칙을 어기면 아무것도 저장되지 않고 상품 코드 번호도 되돌아간다")
    void rollback() {
        productService.create(request(SalesType.NEW, List.of(option(1200, DiscountType.NONE, null, null, List.of()))));
        Map<String, Integer> before = counts();

        // 입력 검사를 건너뛰고 서비스를 바로 불러 같은 치수를 넣는다
        assertThatThrownBy(() -> productService.create(request(SalesType.NEW, List.of(
            option(1500, DiscountType.NONE, null, null, List.of(KEY1)),
            option(1600, DiscountType.NONE, null, null, List.of()),
            option(1500, DiscountType.NONE, null, null, List.of())))))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(counts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT last_no FROM product_code_sequence WHERE prefix = 'RFUR'", Integer.class))
            .isEqualTo(1);
    }

    private Map<String, Integer> counts() {
        return Map.of(
            "product", count("product"),
            "product_option", count("product_option"),
            "product_spec", count("product_spec"),
            "option_image", count("option_image"),
            "product_image", count("product_image"));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private ProductDto.SaveRequest request(SalesType salesType, List<ProductDto.OptionRequest> options) {
        return request(fridge.getId(), salesType, options);
    }

    private static ProductDto.SaveRequest request(Long categoryId, SalesType salesType,
            List<ProductDto.OptionRequest> options) {
        return new ProductDto.SaveRequest(categoryId, "냉장고", salesType, "우성", "국산", null, List.of(),
            List.of(new ProductDto.SpecRequest("소비전력", "1300W")), List.of(KEY1), List.of(), options);
    }

    private static ProductDto.OptionRequest option(int widthMm, DiscountType discountType, Integer discountValue,
            Integer stockQty, List<String> imageKeys) {
        return new ProductDto.OptionRequest(null, null, null, widthMm, 800, 1900, 2_000_000, discountType,
            discountValue, stockQty, KEY1, imageKeys);
    }
}
