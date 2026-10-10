package com.swkitchen.product.repository;

import com.swkitchen.product.domain.ProductOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProductIdOrderByOptionNo(Long productId);
}
