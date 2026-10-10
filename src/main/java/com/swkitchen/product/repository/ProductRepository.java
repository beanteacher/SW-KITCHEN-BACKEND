package com.swkitchen.product.repository;

import com.swkitchen.product.domain.Product;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    boolean existsByCategoryIdIn(Collection<Long> categoryIds);
}
