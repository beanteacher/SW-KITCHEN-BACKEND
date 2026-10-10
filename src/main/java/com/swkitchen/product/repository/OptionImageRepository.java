package com.swkitchen.product.repository;

import com.swkitchen.product.domain.OptionImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionImageRepository extends JpaRepository<OptionImage, Long> {

    List<OptionImage> findByProductOptionIdInOrderBySortOrder(Collection<Long> productOptionIds);
}
