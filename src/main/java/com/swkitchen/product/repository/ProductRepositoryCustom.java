package com.swkitchen.product.repository;

import com.swkitchen.product.domain.Product;
import com.swkitchen.product.dto.ProductDto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    List<ProductDto.CategoryProductCount> countByCategory();

    Page<Product> search(ProductDto.SearchRequest request, Pageable pageable);

    List<ProductDto.CategoryProductCount> countOnSaleByCategory(ProductDto.SearchRequest request);

    List<ProductDto.SalesTypeCount> countOnSaleBySalesType(ProductDto.SearchRequest request);

    List<ProductDto.MaterialCount> countOnSaleByMaterial(ProductDto.SearchRequest request);

    Page<Product> searchAdmin(ProductDto.AdminSearchRequest request, Pageable pageable);
}
