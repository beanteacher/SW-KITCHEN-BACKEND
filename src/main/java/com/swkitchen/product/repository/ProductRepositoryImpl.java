package com.swkitchen.product.repository;

import static com.swkitchen.category.domain.QCategory.category;
import static com.swkitchen.product.domain.QProduct.product;
import static com.swkitchen.product.domain.QProductOption.productOption;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EnumPath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLSubQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.swkitchen.product.domain.Material;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.dto.ProductDto;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    // MySQL 은 문자열 안의 \ 를 따로 해석해서, LIKE 이스케이프 문자로 ! 를 쓴다
    private static final char LIKE_ESCAPE = '!';

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProductDto.CategoryProductCount> countByCategory() {
        return queryFactory
            .select(Projections.constructor(ProductDto.CategoryProductCount.class, product.category.id, product.count()))
            .from(product)
            .groupBy(product.category.id)
            .fetch();
    }

    /** 고객 목록·검색. 재고품·중고품은 보이는 수량이 남은 제품만 나온다 */
    @Override
    public Page<Product> search(ProductDto.SearchRequest request, Pageable pageable) {
        BooleanExpression[] conditions = onSaleConditions(request);

        List<Product> products = queryFactory
            .selectFrom(product)
            .join(product.category, category)
            .where(conditions)
            .orderBy(orderOf(request.orderBy()))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        Long total = queryFactory
            .select(product.count())
            .from(product)
            .join(product.category, category)
            .where(conditions)
            .fetchOne();

        return new PageImpl<>(products, pageable, total);
    }

    /** 중분류별 개수. 분류 조건만 빼고 센다 (필터의 분류 줄). 대분류는 서비스가 아래 중분류를 더한다 */
    @Override
    public List<ProductDto.CategoryProductCount> countOnSaleByCategory(ProductDto.SearchRequest request) {
        return queryFactory
            .select(Projections.constructor(ProductDto.CategoryProductCount.class, category.id, product.count()))
            .from(product)
            .join(product.category, category)
            .where(onSaleConditions(request.withoutCategory()))
            .groupBy(category.id)
            .fetch();
    }

    /** 판매 구분별 개수. 판매 구분 조건만 빼고 센다 */
    @Override
    public List<ProductDto.SalesTypeCount> countOnSaleBySalesType(ProductDto.SearchRequest request) {
        return queryFactory
            .select(Projections.constructor(ProductDto.SalesTypeCount.class, product.salesType, product.count()))
            .from(product)
            .join(product.category, category)
            .where(onSaleConditions(request.withoutSalesType()))
            .groupBy(product.salesType)
            .fetch();
    }

    /** 재질별 개수. 재질 조건만 빼고 센다. 재질이 여럿인 제품은 재질마다 하나씩 센다 */
    @Override
    public List<ProductDto.MaterialCount> countOnSaleByMaterial(ProductDto.SearchRequest request) {
        EnumPath<Material> material = Expressions.enumPath(Material.class, "material");
        return queryFactory
            .select(Projections.constructor(ProductDto.MaterialCount.class, material, product.count()))
            .from(product)
            .join(product.category, category)
            .join(product.materials, material)
            .where(onSaleConditions(request.withoutMaterials()))
            .groupBy(material)
            .fetch();
    }

    /** 신규 등록순. 분류(대분류 포함)는 화면에 이름을 보여주려고 함께 읽는다 */
    @Override
    public Page<Product> searchAdmin(ProductDto.AdminSearchRequest request, Pageable pageable) {
        BooleanExpression[] conditions = {
            adminKeywordMatches(request.keyword()),
            inCategory(request.categoryId()),
            salesTypeIn(request.salesType())
        };

        List<Product> products = queryFactory
            .selectFrom(product)
            .join(product.category, category).fetchJoin()
            .join(category.parent).fetchJoin()
            .where(conditions)
            .orderBy(product.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        Long total = queryFactory
            .select(product.count())
            .from(product)
            .join(product.category, category)
            .where(conditions)
            .fetchOne();

        return new PageImpl<>(products, pageable, total);
    }

    /** 고객 목록 조건. product.category 를 category 로 조인한 쿼리에 건다 */
    private static BooleanExpression[] onSaleConditions(ProductDto.SearchRequest request) {
        return new BooleanExpression[] {
            onSale(),
            keywordMatches(request.keyword()),
            inCategory(request.categoryId()),
            salesTypeIn(request.salesType()),
            hasAnyMaterial(request.materials())
        };
    }

    /** 신제품은 늘 나온다. 재고품·중고품은 보이는 수량(재고 - 잡아 둔 수량)이 남은 옵션이 있어야 한다 */
    private static BooleanExpression onSale() {
        return product.salesType.eq(SalesType.NEW)
            .or(optionExists(productOption.stockQty.subtract(productOption.reservedQty).goe(1)));
    }

    /** 제품명·제조사, 옵션의 상품 코드. 제조사 모델명은 고객에게 보이지 않아 찾지 않는다 */
    private static BooleanExpression keywordMatches(String keyword) {
        if (keyword == null) {
            return null;
        }
        String pattern = likePattern(keyword);
        BooleanExpression matches = product.name.like(pattern, LIKE_ESCAPE)
            .or(product.manufacturer.like(pattern, LIKE_ESCAPE));
        BooleanExpression codeLike = productCodeLike(keyword);
        // 조건 없이 optionExists 를 부르면 옵션이 있는 모든 제품이 맞는다
        return codeLike == null ? matches : matches.or(optionExists(codeLike));
    }

    /** 고른 재질 중 하나라도 가진 제품 */
    private static BooleanExpression hasAnyMaterial(List<Material> materials) {
        if (materials == null || materials.isEmpty()) {
            return null;
        }
        return product.materials.any().in(materials);
    }

    /** 같은 값이면 신규 등록순. 가격순은 옵션 중 가장 낮은 판매가로 줄 세운다. 인기순은 주문 기능 전까지 신규 등록순이다 */
    private static OrderSpecifier<?>[] orderOf(ProductDto.OrderBy orderBy) {
        OrderSpecifier<Long> newest = product.id.desc();
        return switch (orderBy) {
            case POPULAR, NEWEST -> new OrderSpecifier<?>[] {newest};
            case PRICE_ASC -> new OrderSpecifier<?>[] {new OrderSpecifier<>(Order.ASC, lowestSalePrice()), newest};
            case PRICE_DESC -> new OrderSpecifier<?>[] {new OrderSpecifier<>(Order.DESC, lowestSalePrice()), newest};
        };
    }

    private static JPQLSubQuery<Integer> lowestSalePrice() {
        return JPAExpressions
            .select(productOption.price.salePrice.min())
            .from(productOption)
            .where(productOption.product.eq(product));
    }

    /** 제품명, 옵션의 상품 코드·제조사 모델명 */
    private static BooleanExpression adminKeywordMatches(String keyword) {
        if (keyword == null) {
            return null;
        }
        String pattern = likePattern(keyword);
        BooleanExpression optionMatches = optionExists(
            productOption.modelName.like(pattern, LIKE_ESCAPE).or(productCodeLike(keyword)));
        return product.name.like(pattern, LIKE_ESCAPE).or(optionMatches);
    }

    /** 이 제품의 옵션 중 조건에 맞는 것이 있다 */
    private static BooleanExpression optionExists(BooleanExpression condition) {
        return JPAExpressions
            .selectOne()
            .from(productOption)
            .where(productOption.product.eq(product), condition)
            .exists();
    }

    /**
     * 상품 코드 칸은 영문 전용(ascii)이라 한글 검색어와 비교하면 MySQL 이 오류를 낸다.
     * 영문 검색어일 때만 비교하고, 코드는 대문자로만 저장되므로 대문자로 바꿔 찾는다
     */
    private static BooleanExpression productCodeLike(String keyword) {
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(keyword)) {
            return null;
        }
        return productOption.productCode.like(likePattern(keyword.toUpperCase(Locale.ROOT)), LIKE_ESCAPE);
    }

    /** 대분류면 그 아래 중분류 전부, 중분류면 그 중분류만 */
    private static BooleanExpression inCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return category.id.eq(categoryId).or(category.parent.id.eq(categoryId));
    }

    private static BooleanExpression salesTypeIn(List<SalesType> salesTypes) {
        if (salesTypes == null || salesTypes.isEmpty()) {
            return null;
        }
        return product.salesType.in(salesTypes);
    }

    /** 부분 일치. 검색어의 % _ 는 글자 그대로 찾게 한다 */
    private static String likePattern(String keyword) {
        String escaped = keyword
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
