package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductSpec;
import com.swkitchen.product.domain.SalesType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class ProductSpecRepositoryTest {

    @Autowired
    ProductSpecRepository specRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    Product product;

    @BeforeEach
    void setUp() {
        Category top = categoryRepository.saveAndFlush(Category.create(null, "가열기기", "HT", 1));
        Category fryer = categoryRepository.saveAndFlush(Category.create(top, "튀김기", "FR", 1));
        product = productRepository.saveAndFlush(
            Product.create(fryer, "전기 튀김기", SalesType.NEW, "제조사", "한국", "HTFR-00001"));
    }

    @Test
    @DisplayName("사양 줄을 저장한다")
    void save() {
        ProductSpec spec = specRepository.saveAndFlush(ProductSpec.create(product, "소비전력", "1300W", 1));

        assertThat(specRepository.findById(spec.getId())).get()
            .extracting(ProductSpec::getValue).isEqualTo("1300W");
    }

    @Test
    @DisplayName("한 제품에 같은 이름의 사양 줄은 두 개 둘 수 없다")
    void duplicateName() {
        specRepository.saveAndFlush(ProductSpec.create(product, "소비전력", "1300W", 1));

        assertThatThrownBy(() -> specRepository.saveAndFlush(ProductSpec.create(product, "소비전력", "1500W", 2)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
