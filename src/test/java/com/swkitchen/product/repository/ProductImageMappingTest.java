package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import com.swkitchen.product.domain.Dimensions;
import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.OptionImage;
import com.swkitchen.product.domain.Price;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductDetailImage;
import com.swkitchen.product.domain.ProductImage;
import com.swkitchen.product.domain.ProductOption;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.domain.ProductSpec;
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
        Product product = Product.create(fridge, "냉장고", SalesType.NEW, "제조사", "한국", "설명", "RFUR-00001");
        em.persist(top);
        em.persist(fridge);
        em.persist(product);
        ProductOption option = ProductOption.create(
            product,
            null,
            new Dimensions(900, 600, 800),
            Price.of(10_000, DiscountType.NONE, null),
            null,
            "products/main.jpg");
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

    @Test
    @DisplayName("제품의 옵션·사양·이미지와 옵션 이미지를 다시 읽으면 순서대로 나온다")
    void childrenInOrder() {
        Category top = Category.create(null, "냉장·냉동", "RF", 1);
        Category fridge = Category.create(top, "업소용 냉장고", "UR", 1);
        Product product = Product.create(fridge, "냉장고", SalesType.STOCK, "제조사", "한국", null, "RFUR-00001");
        em.persist(top);
        em.persist(fridge);
        em.persist(product);
        ProductOption first = option(product, 900);
        ProductOption second = option(product, 1200);
        em.persist(first);
        em.persist(second);
        em.persist(ProductSpec.create(product, "전압", "220V", 2));
        em.persist(ProductSpec.create(product, "소비전력", "1300W", 1));
        em.persist(ProductImage.create(product, "products/b.jpg", 2));
        em.persist(ProductImage.create(product, "products/a.jpg", 1));
        em.persist(ProductDetailImage.create(product, "products/d.jpg", 1));
        em.persist(OptionImage.create(second, "products/f.jpg", 2));
        em.persist(OptionImage.create(second, "products/e.jpg", 1));
        em.flush();
        em.clear();

        Product saved = em.find(Product.class, product.getId());

        assertThat(saved.getOptions()).extracting(ProductOption::getOptionNo).containsExactly(1, 2);
        assertThat(saved.getSpecs()).extracting(ProductSpec::getName).containsExactly("소비전력", "전압");
        assertThat(saved.getImages()).extracting(ProductImage::getImageKey).containsExactly("products/a.jpg", "products/b.jpg");
        assertThat(saved.getDetailImages()).extracting(ProductDetailImage::getImageKey).containsExactly("products/d.jpg");
        assertThat(saved.getOptions().get(0).getImages()).isEmpty();
        assertThat(saved.getOptions().get(1).getImages())
            .extracting(OptionImage::getImageKey).containsExactly("products/e.jpg", "products/f.jpg");
    }

    private static ProductOption option(Product product, int widthMm) {
        return ProductOption.create(
            product,
            null,
            new Dimensions(widthMm, 600, 800),
            Price.of(10_000, DiscountType.NONE, null),
            1,
            "products/main.jpg");
    }
}
