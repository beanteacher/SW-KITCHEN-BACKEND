package com.swkitchen.product.repository;

import com.swkitchen.product.domain.ProductSpec;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSpecRepository extends JpaRepository<ProductSpec, Long> {

    List<ProductSpec> findByProductIdOrderBySortOrder(Long productId);
}
