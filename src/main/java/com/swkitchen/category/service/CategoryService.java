package com.swkitchen.category.service;

import com.swkitchen.category.domain.Category;
import com.swkitchen.category.dto.CategoryDto;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryDto.TreeResponse> getTree() {
        List<Category> categories = findAllSorted();

        return categories.stream()
            .filter(Category::isTop)
            .map(top -> CategoryDto.TreeResponse.of(top, childrenOf(categories, top).stream()
                .map(child -> CategoryDto.TreeResponse.of(child, List.of()))
                .toList()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto.AdminTreeResponse> getAdminTree() {
        List<Category> categories = findAllSorted();
        Map<Long, Long> productCounts = productRepository.countByCategory().stream()
            .collect(Collectors.toMap(ProductDto.CategoryProductCount::categoryId,
                ProductDto.CategoryProductCount::productCount));

        return categories.stream()
            .filter(Category::isTop)
            .map(top -> CategoryDto.AdminTreeResponse.of(top, productCounts, childrenOf(categories, top).stream()
                .map(child -> CategoryDto.AdminTreeResponse.of(child, productCounts, List.of()))
                .toList()))
            .toList();
    }

    // 분류는 수십 개라 한 번에 읽어 메모리에서 트리로 묶는다
    private List<Category> findAllSorted() {
        return categoryRepository.findAll(Sort.by("sortOrder", "id"));
    }

    private List<Category> childrenOf(List<Category> categories, Category parent) {
        return categories.stream()
            .filter(category -> parent.getId().equals(category.getParentId()))
            .toList();
    }
}
