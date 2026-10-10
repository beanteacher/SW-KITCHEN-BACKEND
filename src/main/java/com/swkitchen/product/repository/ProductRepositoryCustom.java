package com.swkitchen.product.repository;

import com.swkitchen.product.dto.ProductDto;
import java.util.List;

public interface ProductRepositoryCustom {

    List<ProductDto.CategoryProductCount> countByCategory();
}
