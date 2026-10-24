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
    @DisplayName("고객 목록: 신제품은 늘 나오고, 재고품·중고품은 보이는 수량(재고 - 잡아 둔 수량)이 남은 옵션이 있어야 나온다")
    void searchOnlyOnSale() {
        Product newOne = saveWithOption(fridge, "RFUR-00001", "신제품", SalesType.NEW, null);
        Product left = saveWithOption(fridge, "RFUR-00002", "남은 중고", SalesType.USED, null);
        Product reserved = saveWithOption(fridge, "RFUR-00003", "잡아 둔 재고", SalesType.STOCK, null);
        Product soldOut = product(fridge, "RFUR-00004");
        soldOut.change(fridge, "재고 0", SalesType.STOCK, "제조사", "한국", null);
        productRepository.save(soldOut);
        addOption(soldOut, 900, 10_000, 0);
        // 잡아 둔 수량은 주방 설계 기능이 바꾼다. 아직 없어 DB 에 직접 넣는다
        jdbc.update("UPDATE product_option SET reserved_qty = 1 WHERE product_id = ?", reserved.getId());

        assertThat(search(request(null, null, null, null, null))).containsExactly(left.getId(), newOne.getId());
    }

    @Test
    @DisplayName("고객 목록: 제품명·제조사·상품 코드로 찾고, 제조사 모델명으로는 찾지 않는다")
    void searchByKeyword() {
        Product fridgeOne = saveWithOption(fridge, "RFUR-00001", "업소용 냉장고", SalesType.NEW, "WSM-1243RF");
        Product other = productRepository.save(
            Product.create(freezer, "냉동고", SalesType.NEW, "우성", "한국", null, "RFUF-00002"));
        addOption(other, 900, 10_000, null);

        assertThat(search(request(null, null, null, "냉장", null))).containsExactly(fridgeOne.getId());
        assertThat(search(request(null, null, null, "우성", null))).containsExactly(other.getId());
        assertThat(search(request(null, null, null, "rfuf-00002", null))).containsExactly(other.getId());
        assertThat(search(request(null, null, null, "1243RF", null))).isEmpty();
    }

    @Test
    @DisplayName("고객 목록: 판매 구분·분류로 거르고, 재질은 고른 것 중 하나라도 가진 제품이다")
    void searchByFilters() {
        Product stainless = saveWithOption(fridge, "RFUR-00001", "냉장고", SalesType.NEW, null);
        stainless.changeMaterials(List.of(Material.STAINLESS, Material.PLASTIC));
        Product steel = saveWithOption(freezer, "RFUF-00002", "냉동고", SalesType.STOCK, null);
        steel.changeMaterials(List.of(Material.STEEL));
        Product none = saveWithOption(freezer, "RFUF-00003", "냉동고", SalesType.USED, null);
        productRepository.flush();

        assertThat(search(request(List.of(SalesType.STOCK, SalesType.USED), null, null, null, null)))
            .containsExactly(none.getId(), steel.getId());
        assertThat(search(request(null, fridge.getParent().getId(), null, null, null)))
            .containsExactly(none.getId(), steel.getId(), stainless.getId());
        assertThat(search(request(null, freezer.getId(), null, null, null))).containsExactly(none.getId(), steel.getId());
        assertThat(search(request(null, null, List.of(Material.PLASTIC, Material.STEEL), null, null)))
            .containsExactly(steel.getId(), stainless.getId());
    }

    @Test
    @DisplayName("고객 목록: 가격순은 옵션 중 가장 낮은 판매가로 줄 세우고, 같으면 신규 등록순이다. 인기순은 신규 등록순이다")
    void searchOrder() {
        Product cheap = productRepository.save(product(fridge, "RFUR-00001"));
        addOption(cheap, 900, 30_000, null);
        addOption(cheap, 1200, 5_000, null);
        Product middle = productRepository.save(product(fridge, "RFUR-00002"));
        addOption(middle, 900, 10_000, null);
        Product sameAsMiddle = productRepository.save(product(fridge, "RFUR-00003"));
        addOption(sameAsMiddle, 900, 10_000, null);

        assertThat(search(request(null, null, null, null, ProductDto.OrderBy.PRICE_ASC)))
            .containsExactly(cheap.getId(), sameAsMiddle.getId(), middle.getId());
        assertThat(search(request(null, null, null, null, ProductDto.OrderBy.PRICE_DESC)))
            .containsExactly(sameAsMiddle.getId(), middle.getId(), cheap.getId());
        assertThat(search(request(null, null, null, null, null)))
            .containsExactly(sameAsMiddle.getId(), middle.getId(), cheap.getId());
    }

    @Test
    @DisplayName("필터 개수: 그 줄의 조건만 빼고 다른 조건은 그대로 둔 채 분류·판매 구분·재질별로 센다. 팔 수 없는 재고품은 세지 않는다")
    void countOnSale() {
        Category kitchen = categoryRepository.saveAndFlush(Category.create(null, "조리", "KT", 2));
        Category range = categoryRepository.saveAndFlush(Category.create(kitchen, "가스레인지", "GR", 1));
        Product a = saveWithOption(fridge, "RFUR-00001", "냉장고", SalesType.NEW, null);
        a.changeMaterials(List.of(Material.STAINLESS, Material.PLASTIC));
        Product b = saveWithOption(freezer, "RFUF-00002", "냉동고", SalesType.STOCK, null);
        b.changeMaterials(List.of(Material.STAINLESS));
        Product c = saveWithOption(range, "KTGR-00003", "레인지", SalesType.NEW, null);
        c.changeMaterials(List.of(Material.STEEL));
        Product soldOut = product(freezer, "RFUF-00004");
        soldOut.change(freezer, "재고 0", SalesType.STOCK, "제조사", "한국", null);
        soldOut.changeMaterials(List.of(Material.STAINLESS));
        productRepository.save(soldOut);
        addOption(soldOut, 900, 10_000, 0);
        productRepository.flush();

        // 재질 STAINLESS 를 고른 상태: 분류·판매 구분 개수는 재질 조건을 지키고, 재질 개수는 재질 조건 없이 센다
        ProductDto.SearchRequest stainless = request(null, null, List.of(Material.STAINLESS), null, null);

        assertThat(productRepository.countOnSaleByCategory(stainless)).containsExactlyInAnyOrder(
            new ProductDto.CategoryProductCount(fridge.getId(), 1),
            new ProductDto.CategoryProductCount(freezer.getId(), 1));
        assertThat(productRepository.countOnSaleBySalesType(stainless)).containsExactlyInAnyOrder(
            new ProductDto.SalesTypeCount(SalesType.NEW, 1),
            new ProductDto.SalesTypeCount(SalesType.STOCK, 1));
        assertThat(productRepository.countOnSaleByMaterial(stainless)).containsExactlyInAnyOrder(
            new ProductDto.MaterialCount(Material.STAINLESS, 2),
            new ProductDto.MaterialCount(Material.PLASTIC, 1),
            new ProductDto.MaterialCount(Material.STEEL, 1));

        // 분류를 고른 상태: 재질 개수는 그 분류 안에서 센다
        assertThat(productRepository.countOnSaleByMaterial(request(null, kitchen.getId(), null, null, null)))
            .containsExactly(new ProductDto.MaterialCount(Material.STEEL, 1));
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

    private List<Long> search(ProductDto.SearchRequest request) {
        return productRepository.search(request, PageRequest.of(0, 20))
            .map(Product::getId)
            .getContent();
    }

    private static ProductDto.SearchRequest request(
            List<SalesType> salesTypes,
            Long categoryId,
            List<Material> materials,
            String keyword,
            ProductDto.OrderBy orderBy) {
        return new ProductDto.SearchRequest(salesTypes, categoryId, materials, keyword, orderBy);
    }

    private void addOption(Product product, int widthMm, int listPrice, Integer stockQty) {
        optionRepository.saveAndFlush(ProductOption.create(
            product,
            null,
            new Dimensions(widthMm, 600, 800),
            Price.of(listPrice, DiscountType.NONE, null),
            stockQty,
            "products/a.jpg"));
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
