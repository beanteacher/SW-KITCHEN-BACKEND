package com.swkitchen.product.repository;

import static com.swkitchen.product.domain.QProduct.product;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.swkitchen.product.dto.ProductDto;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProductDto.CategoryProductCount> countByCategory() {
        return queryFactory
            .select(Projections.constructor(ProductDto.CategoryProductCount.class, product.categoryId, product.count()))
            .from(product)
            .groupBy(product.categoryId)
            .fetch();
    }
}
