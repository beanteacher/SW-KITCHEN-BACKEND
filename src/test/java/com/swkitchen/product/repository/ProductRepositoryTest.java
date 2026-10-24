package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.product.domain.Dimensions;
import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.Material;
import com.swkitchen.product.domain.Price;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductOption;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.dto.ProductDto;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@RepositoryTest
class ProductRepositoryTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductOptionRepository optionRepository;

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
        fridge = categoryRepository.saveAndFlush(Category.create(top, "업소용 냉장고", "UR", 1));
        freezer = categoryRepository.saveAndFlush(Category.create(top, "업소용 냉동고", "UF", 2));
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
            Product.create(em.getReference(Category.class, 999_999L), "냉장고", SalesType.NEW, "제조사", "한국", null, "RFUR-00001")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("제품이 연결된 분류는 DB 가 삭제를 막는다")
    void categoryInUseCannotBeDeleted() {
        productRepository.saveAndFlush(product(fridge, "RFUR-00001"));
        em.clear();

        assertThatThrownBy(() -> {
            categoryRepository.deleteById(fridge.getId());
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

    @Test
    @DisplayName("관리 목록: 제품명·상품 코드(소문자로 써도)·모델명으로 찾고 제조사로는 찾지 않는다. 검색어의 % 는 글자 그대로 찾는다")
    void searchAdminByKeyword() {
        Product discount = saveWithOption(fridge, "RFUR-00001", "50% 할인 냉장고", SalesType.NEW, "WSM-1243RF");
        Product other = saveWithOption(freezer, "RFUF-00002", "500 냉동고", SalesType.STOCK, null);

        assertThat(searchAdmin("0%", null, null)).containsExactly(discount.getId());
        assertThat(searchAdmin("uf-00002", null, null)).containsExactly(other.getId());
        assertThat(searchAdmin("1243rf", null, null)).containsExactly(discount.getId());
        assertThat(searchAdmin("냉동", null, null)).containsExactly(other.getId());
        assertThat(searchAdmin("제조사", null, null)).isEmpty();
    }

    @Test
    @DisplayName("관리 목록: 대분류는 아래 중분류 제품을 모두 찾고, 판매 구분은 여러 개를 고를 수 있다")
    void searchAdminByCategoryAndSalesType() {
        Product newOne = saveWithOption(fridge, "RFUR-00001", "냉장고", SalesType.NEW, null);
        Product stock = saveWithOption(freezer, "RFUF-00002", "냉동고", SalesType.STOCK, null);
        Product used = saveWithOption(freezer, "RFUF-00003", "냉동고", SalesType.USED, null);

        assertThat(searchAdmin(null, fridge.getParent().getId(), null))
            .containsExactly(used.getId(), stock.getId(), newOne.getId());
        assertThat(searchAdmin(null, fridge.getId(), null)).containsExactly(newOne.getId());
        assertThat(searchAdmin(null, null, List.of(SalesType.STOCK, SalesType.USED)))
            .containsExactly(used.getId(), stock.getId());
    }

    @Test
    @DisplayName("관리 목록: 신규 등록순으로 페이지를 나누고 전체 개수를 센다")
    void searchAdminPage() {
        saveWithOption(fridge, "RFUR-00001", "냉장고", SalesType.NEW, null);
        Product second = saveWithOption(fridge, "RFUR-00002", "냉장고", SalesType.NEW, null);
        Product third = saveWithOption(fridge, "RFUR-00003", "냉장고", SalesType.NEW, null);

        Page<Product> page = productRepository.searchAdmin(
            new ProductDto.AdminSearchRequest(null, fridge.getId(), null),
            PageRequest.of(0, 2));

        assertThat(page.getContent()).extracting(Product::getId).containsExactly(third.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private List<Long> searchAdmin(String keyword, Long categoryId, List<SalesType> salesTypes) {
        return productRepository.searchAdmin(
                new ProductDto.AdminSearchRequest(keyword, categoryId, salesTypes),
                PageRequest.of(0, 20))
            .map(Product::getId)
            .getContent();
    }

    private Product saveWithOption(Category category, String codePrefix, String name, SalesType salesType, String modelName) {
        Product product = productRepository.save(
            Product.create(category, name, salesType, "제조사", "한국", null, codePrefix));
        optionRepository.saveAndFlush(ProductOption.create(
            product,
            modelName,
            new Dimensions(900, 600, 800),
            Price.of(10_000, DiscountType.NONE, null),
            salesType == SalesType.NEW ? null : 1,
            "products/a.jpg"));
        return product;
    }

    private Product product(Category category, String codePrefix) {
        return Product.create(category, "냉장고", SalesType.NEW, "제조사", "한국", null, codePrefix);
    }
}
