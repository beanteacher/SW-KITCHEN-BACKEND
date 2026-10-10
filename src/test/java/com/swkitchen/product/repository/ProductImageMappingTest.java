package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.OptionImage;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductDetailImage;
import com.swkitchen.product.domain.ProductImage;
import com.swkitchen.product.domain.ProductOption;
import com.swkitchen.product.domain.SalesType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 이미지는 제품 등록 때 제품과 함께 저장할 예정이라 저장소가 아직 없다. EntityManager 로 매핑만 확인한다
@RepositoryTest
class ProductImageMappingTest {

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("제품·상세·옵션 이미지를 저장하고 다시 읽는다")
    void saveAndRead() {
        Category top = Category.create(null, "냉장·냉동", "RF", 1);
        Category fridge = Category.create(top, "업소용 냉장고", "UR", 1);
        Product product = Product.create(fridge, "냉장고", SalesType.NEW, "제조사", "한국", "RFUR-00001");
        em.persist(top);
        em.persist(fridge);
        em.persist(product);
        ProductOption option = ProductOption.create(product, null, 900, 600, 800, 10_000, DiscountType.NONE, null,
            null, "products/main.jpg");
        em.persist(option);

        ProductImage image = ProductImage.create(product, "products/a.jpg", 1);
        ProductDetailImage detail = ProductDetailImage.create(product, "products/b.jpg", 1);
        OptionImage optionImage = OptionImage.create(option, "products/c.jpg", 2);
        em.persist(image);
        em.persist(detail);
        em.persist(optionImage);
        em.flush();
        em.clear();

        assertThat(em.find(ProductImage.class, image.getId()).getProduct().getId()).isEqualTo(product.getId());
        assertThat(em.find(ProductDetailImage.class, detail.getId()).getImageKey()).isEqualTo("products/b.jpg");
        OptionImage saved = em.find(OptionImage.class, optionImage.getId());
        assertThat(saved.getProductOption().getId()).isEqualTo(option.getId());
        assertThat(saved.getSortOrder()).isEqualTo(2);
    }
}
