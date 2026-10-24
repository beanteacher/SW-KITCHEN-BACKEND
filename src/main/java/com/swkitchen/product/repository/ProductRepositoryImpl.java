package com.swkitchen.product.repository;

import static com.swkitchen.category.domain.QCategory.category;
import static com.swkitchen.product.domain.QProduct.product;
import static com.swkitchen.product.domain.QProductOption.productOption;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
