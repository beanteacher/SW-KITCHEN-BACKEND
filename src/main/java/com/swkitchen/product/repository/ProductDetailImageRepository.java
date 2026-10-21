package com.swkitchen.product.repository;

import com.swkitchen.product.domain.ProductDetailImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductDetailImageRepository extends JpaRepository<ProductDetailImage, Long> {

    List<ProductDetailImage> findByProductIdOrderBySortOrder(Long productId);
}
