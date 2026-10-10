package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.product.domain.Material;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.dto.ProductDto;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@RepositoryTest
class ProductRepositoryTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    Category fridge;
    Category freezer;

    @BeforeEach
    void setUp() {
        Category top = categoryRepository.saveAndFlush(Category.create(null, "냉장·냉동", "RF", 1));
        fridge = categoryRepository.saveAndFlush(Category.create(top.getId(), "업소용 냉장고", "UR", 1));
        freezer = categoryRepository.saveAndFlush(Category.create(top.getId(), "업소용 냉동고", "UF", 2));
    }

    @Test
    @DisplayName("분류별 제품 수를 센다")
    void countByCategory() {
        productRepository.saveAndFlush(product(fridge, "RFUR-00001"));
        productRepository.saveAndFlush(product(fridge, "RFUR-00002"));
        productRepository.saveAndFlush(product(freezer, "RFUF-00003"));

        assertThat(productRepository.countByCategory()).containsExactlyInAnyOrder(
            new ProductDto.CategoryProductCount(fridge.getId(), 2),
            new ProductDto.CategoryProductCount(freezer.getId(), 1));
    }

    @Test
    @DisplayName("분류들 중 하나라도 제품이 있는지 본다")
    void existsByCategoryIdIn() {
        productRepository.saveAndFlush(product(fridge, "RFUR-00001"));

        assertThat(productRepository.existsByCategoryIdIn(List.of(fridge.getId(), freezer.getId()))).isTrue();
        assertThat(productRepository.existsByCategoryIdIn(List.of(freezer.getId()))).isFalse();
    }

    @Test
    @DisplayName("없는 분류의 제품은 저장할 수 없다")
    void categoryMustExist() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(
            Product.create(999_999L, "냉장고", SalesType.NEW, "제조사", "한국", "RFUR-00001")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("제품이 연결된 분류는 DB 가 삭제를 막는다")
    void categoryInUseCannotBeDeleted() {
        productRepository.saveAndFlush(product(fridge, "RFUR-00001"));

        assertThatThrownBy(() -> {
            categoryRepository.delete(fridge);
            categoryRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("상품 코드 앞부분은 겹칠 수 없다")
    void duplicateCodePrefix() {
        productRepository.saveAndFlush(product(fridge, "RFUR-00001"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product(freezer, "RFUR-00001")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정해진 판매 구분 외의 값은 DB 가 막는다")
    void salesTypeCheck() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO product (category_id, name, sales_type, manufacturer, origin, code_prefix, created_at, updated_at) "
                + "VALUES (?, '냉장고', 'RENTAL', '제조사', '한국', 'RFUR-00001', NOW(6), NOW(6))", fridge.getId()))
            .hasMessageContaining("ck_product_sales_type");
    }

    @Test
    @DisplayName("재질을 여러 개 저장하고 바꿀 수 있다. enum 의 모든 값을 DB 가 받는다")
    void materials() {
        Product product = product(fridge, "RFUR-00001");
        product.changeMaterials(List.of(Material.values()));
        productRepository.saveAndFlush(product);
        em.clear();

        assertThat(productRepository.findById(product.getId()).orElseThrow().getMaterials())
            .containsExactlyInAnyOrder(Material.values());

        Product saved = productRepository.findById(product.getId()).orElseThrow();
        saved.changeMaterials(List.of(Material.STAINLESS));
        productRepository.flush();
        em.clear();

        assertThat(productRepository.findById(product.getId()).orElseThrow().getMaterials())
            .containsExactly(Material.STAINLESS);
    }

    @Test
    @DisplayName("정해진 재질 외의 값은 DB 가 막는다")
    void materialCheck() {
        Product product = productRepository.saveAndFlush(product(fridge, "RFUR-00001"));

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO product_material (product_id, material) VALUES (?, 'WOOD')", product.getId()))
            .hasMessageContaining("ck_product_material");
    }

    private Product product(Category category, String codePrefix) {
        return Product.create(category.getId(), "냉장고", SalesType.NEW, "제조사", "한국", codePrefix);
    }
}
