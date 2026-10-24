package com.swkitchen.product.repository;

import com.swkitchen.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
}
