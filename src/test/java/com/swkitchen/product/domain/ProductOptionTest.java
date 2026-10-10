package com.swkitchen.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.swkitchen.category.domain.Category;
import org.junit.jupiter.api.DisplayName;
import com.swkitchen.category.domain.Category;
import org.junit.jupiter.api.Test;

class ProductOptionTest {

    @Test
    @DisplayName("정률 17% → 12,450원에서 100원 미만 버림")
    void rate() {
        assertThat(ProductOption.salePrice(15_000, DiscountType.RATE, 17)).isEqualTo(12_400);
    }

    @Test
    @DisplayName("정액 할인은 정상가에서 뺀다")
    void amount() {
        assertThat(ProductOption.salePrice(15_000, DiscountType.AMOUNT, 3_000)).isEqualTo(12_000);
    }

    @Test
    @DisplayName("할인이 없으면 정상가 그대로")
    void none() {
        assertThat(ProductOption.salePrice(15_050, DiscountType.NONE, null)).isEqualTo(15_050);
    }

    @Test
    @DisplayName("정상가가 커도 정률 계산이 넘치지 않는다")
    void largeRate() {
        assertThat(ProductOption.salePrice(2_000_000_000, DiscountType.RATE, 1)).isEqualTo(1_980_000_000);
    }

    @Test
    @DisplayName("옵션을 만들 때마다 번호를 하나씩 발급해 상품 코드를 붙인다")
    void productCode() {
        Product product = Product.create(Category.create(null, "냉장·냉동", "RF", 1), "냉장고", SalesType.NEW, "제조사", "한국", "RFUR-00123");

        ProductOption first = option(product);
        ProductOption second = option(product);

        assertThat(first.getProductCode()).isEqualTo("RFUR-00123-01");
        assertThat(second.getProductCode()).isEqualTo("RFUR-00123-02");
        assertThat(second.getProduct()).isSameAs(product);
        assertThat(product.getLastOptionNo()).isEqualTo(2);
    }

    private ProductOption option(Product product) {
        return ProductOption.create(product, null, 900, 600, 800, 10_000, DiscountType.NONE, null, null,
            "products/a.jpg");
    }
}
