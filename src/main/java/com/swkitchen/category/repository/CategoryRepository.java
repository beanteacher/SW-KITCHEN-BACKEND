package com.swkitchen.category.repository;

import com.swkitchen.category.domain.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// parentId 가 null 이면 대분류끼리 찾는다 (parent_id IS NULL)
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentIdAndAbbr(Long parentId, String abbr);

    Optional<Category> findTopByParentIdOrderBySortOrderDesc(Long parentId);
}
