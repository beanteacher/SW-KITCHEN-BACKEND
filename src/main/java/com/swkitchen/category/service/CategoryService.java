package com.swkitchen.category.service;

import com.swkitchen.category.domain.Category;
import com.swkitchen.category.dto.CategoryDto;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.dto.ErrorResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.repository.ProductRepository;
import java.util.ArrayList;
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

    /** 형제 분류 중 마지막 순서로 넣는다 */
    public CategoryDto.Response create(CategoryDto.CreateRequest request) {
        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND,
                    List.of(new ErrorResponse.FieldError("parentId", ErrorCode.CATEGORY_NOT_FOUND.getMessage()))));
            if (!parent.isTop()) {
                throw new AppException(ErrorCode.CATEGORY_NOT_MIDDLE,
                    List.of(new ErrorResponse.FieldError("parentId", "중분류 아래에는 분류를 만들 수 없습니다.")));
            }
        }
        // ponytail: 동시에 같은 약어를 넣으면 DB 유일 제약이 막고 500 이 난다. 자주 생기면 DataIntegrityViolationException 을 409 로 바꾼다
        if (categoryRepository.existsByParentIdAndAbbr(request.parentId(), request.abbr())) {
            throw new AppException(ErrorCode.CATEGORY_ABBR_DUPLICATE,
                List.of(new ErrorResponse.FieldError("abbr", ErrorCode.CATEGORY_ABBR_DUPLICATE.getMessage())));
        }

        int sortOrder = categoryRepository.findTopByParentIdOrderBySortOrderDesc(request.parentId())
            .map(last -> last.getSortOrder() + 1)
            .orElse(1);
        Category category = categoryRepository.save(
            Category.create(request.parentId(), request.name(), request.abbr(), sortOrder));

        return CategoryDto.Response.from(category);
    }

    /** 제품이 연결된 분류(대분류는 아래 중분류 포함)는 약어를 바꿀 수 없다 */
    public CategoryDto.Response update(Long id, CategoryDto.UpdateRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (request.abbr() != null && !request.abbr().equals(category.getAbbr())) {
            if (hasProducts(category)) {
                throw new AppException(ErrorCode.CATEGORY_ABBR_LOCKED,
                    List.of(new ErrorResponse.FieldError("abbr", ErrorCode.CATEGORY_ABBR_LOCKED.getMessage())));
            }
            if (categoryRepository.existsByParentIdAndAbbr(category.getParentId(), request.abbr())) {
                throw new AppException(ErrorCode.CATEGORY_ABBR_DUPLICATE,
                    List.of(new ErrorResponse.FieldError("abbr", ErrorCode.CATEGORY_ABBR_DUPLICATE.getMessage())));
            }
            category.changeAbbr(request.abbr());
        }
        if (request.name() != null) {
            category.changeName(request.name());
        }

        return CategoryDto.Response.from(category);
    }

    private boolean hasProducts(Category category) {
        List<Long> categoryIds = new ArrayList<>(List.of(category.getId()));
        if (category.isTop()) {
            categoryRepository.findByParentId(category.getId()).forEach(child -> categoryIds.add(child.getId()));
        }
        return productRepository.existsByCategoryIdIn(categoryIds);
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
