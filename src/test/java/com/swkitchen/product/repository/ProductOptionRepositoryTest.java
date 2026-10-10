package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductOption;
import com.swkitchen.product.domain.SalesType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@RepositoryTest
class ProductOptionRepositoryTest {

    @Autowired
    ProductOptionRepository optionRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    Product product;

    @BeforeEach
    void setUp() {
        Category top = categoryRepository.saveAndFlush(Category.create(null, "작업대·싱크", "WS", 1));
        Category table = categoryRepository.saveAndFlush(Category.create(top, "작업대", "WT", 1));
        product = productRepository.saveAndFlush(
            Product.create(table, "스텐 작업대", SalesType.USED, "대성", "국산", "WSWT-00015"));
    }

    @Test
    @DisplayName("옵션을 저장하고 다시 읽는다. 판매가가 함께 저장된다")
    void saveAndRead() {
        ProductOption option = optionRepository.saveAndFlush(ProductOption.create(
            product, "WSM-1243RF", 900, 600, 800, 15_000, DiscountType.RATE, 17, 1, "products/a.jpg"));
        em.clear();

        ProductOption saved = optionRepository.findById(option.getId()).orElseThrow();
        assertThat(saved.getProductCode()).isEqualTo("WSWT-00015-01");
        assertThat(saved.getSalePrice()).isEqualTo(12_400);
        assertThat(saved.getStockQty()).isEqualTo(1);
        assertThat(saved.getReservedQty()).isZero();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @DisplayName("옵션을 바꾸면 version 이 1 오른다")
    void versionIncreases() {
        ProductOption option = optionRepository.saveAndFlush(option(900, 15_000, DiscountType.NONE, null));

        option.changePrice(20_000, DiscountType.AMOUNT, 1_000);
        optionRepository.flush();

        assertThat(option.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 제품에 같은 치수의 옵션은 두 개 둘 수 없다")
    void duplicateSize() {
        optionRepository.saveAndFlush(option(900, 15_000, DiscountType.NONE, null));

        assertThatThrownBy(() -> optionRepository.saveAndFlush(option(900, 20_000, DiscountType.NONE, null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정률 100% 는 DB 가 막는다")
    void rateCheck() {
        assertThatThrownBy(() -> optionRepository.saveAndFlush(option(900, 10_000, DiscountType.RATE, 100)))
            .hasMessageContaining("ck_product_option_discount");
    }

    @Test
    @DisplayName("정상가와 같은 정액 할인은 DB 가 막는다")
    void amountCheck() {
        assertThatThrownBy(() -> optionRepository.saveAndFlush(option(900, 10_000, DiscountType.AMOUNT, 10_000)))
            .hasMessageContaining("ck_product_option_discount");
    }

    @Test
    @DisplayName("잡아 둔 수량이 재고보다 많으면 DB 가 막는다")
    void reservedOverStock() {
        ProductOption option = optionRepository.saveAndFlush(option(900, 10_000, DiscountType.NONE, null));

        // 잡아 두는 기능이 아직 없어 수량을 직접 바꾼다
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE product_option SET reserved_qty = 2 WHERE id = ?", option.getId()))
            .hasMessageContaining("ck_product_option_qty");
    }

    private ProductOption option(int widthMm, int listPrice, DiscountType discountType, Integer discountValue) {
        return ProductOption.create(product, null, widthMm, 600, 800, listPrice, discountType, discountValue, 1,
            "products/a.jpg");
    }
}
