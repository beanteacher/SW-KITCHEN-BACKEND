package com.swkitchen.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.swkitchen.category.domain.Category;
import com.swkitchen.category.dto.CategoryDto;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    CategoryService categoryService;

    Category fridge;
    Category kitchen;
    Category upright;
    Category table;

    @BeforeEach
    void setUp() {
        fridge = category(1L, null, "냉장·냉동", "RF", 1);
        kitchen = category(2L, null, "조리", "KT", 2);
        upright = category(11L, fridge, "업소용 냉장고", "UR", 1);
        table = category(12L, fridge, "테이블 냉장고", "TB", 2);
    }

    // ── 트리 ──

    @Test
    @DisplayName("관리용 트리: 중분류는 자기 제품 수, 대분류는 아래 중분류 합, 제품이 없으면 0")
    void adminTreeProductCount() {
        given(categoryRepository.findAll(any(Sort.class))).willReturn(List.of(fridge, upright, table, kitchen));
        given(productRepository.countByCategory()).willReturn(List.of(
            new ProductDto.CategoryProductCount(11L, 2), new ProductDto.CategoryProductCount(12L, 1)));

        List<CategoryDto.AdminTreeResponse> tree = categoryService.getAdminTree();

        assertThat(tree).extracting(CategoryDto.AdminTreeResponse::productCount).containsExactly(3L, 0L);
        assertThat(tree.get(0).children()).extracting(CategoryDto.AdminTreeResponse::productCount)
            .containsExactly(2L, 1L);
        assertThat(tree.get(1).children()).isEmpty();
    }

    // ── 등록 ──

    @Test
    @DisplayName("등록: 형제 중 마지막 순서 + 1 로 넣는다")
    void createAfterLastSibling() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(fridge));
        given(categoryRepository.findTopByParentIdOrderBySortOrderDesc(1L)).willReturn(Optional.of(table));
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        CategoryDto.Response response = categoryService.create(new CategoryDto.CreateRequest(1L, "쇼케이스", "SC"));

        assertThat(response.parentId()).isEqualTo(1L);
        assertThat(response.sortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("등록: 형제가 없으면 순서는 1")
    void createFirst() {
        given(categoryRepository.findTopByParentIdOrderBySortOrderDesc(null)).willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        CategoryDto.Response response = categoryService.create(new CategoryDto.CreateRequest(null, "세척", "WS"));

        assertThat(response.parentId()).isNull();
        assertThat(response.sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("등록: 이미 있는 대분류 약어 RF 로 대분류를 추가하면 CATEGORY_ABBR_DUPLICATE")
    void createDuplicateAbbr() {
        given(categoryRepository.existsByParentIdAndAbbr(null, "RF")).willReturn(true);

        assertErrorCode(() -> categoryService.create(new CategoryDto.CreateRequest(null, "냉장", "RF")),
            ErrorCode.CATEGORY_ABBR_DUPLICATE);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 없는 부모는 CATEGORY_NOT_FOUND")
    void createParentNotFound() {
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertErrorCode(() -> categoryService.create(new CategoryDto.CreateRequest(999L, "세척", "WS")),
            ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("등록: 중분류 아래에는 만들 수 없다 (CATEGORY_NOT_MIDDLE)")
    void createUnderMiddle() {
        given(categoryRepository.findById(11L)).willReturn(Optional.of(upright));

        assertErrorCode(() -> categoryService.create(new CategoryDto.CreateRequest(11L, "세척", "WS")),
            ErrorCode.CATEGORY_NOT_MIDDLE);
        verify(categoryRepository, never()).save(any());
    }

    // ── 수정 ──

    @Test
    @DisplayName("수정: 보낸 값만 바꾸고 안 보낸 값은 그대로")
    void updateOnlySentValues() {
        given(categoryRepository.findById(2L)).willReturn(Optional.of(kitchen));

        categoryService.update(2L, new CategoryDto.UpdateRequest("조리기기", null));

        assertThat(kitchen.getName()).isEqualTo("조리기기");
        assertThat(kitchen.getAbbr()).isEqualTo("KT");
    }

    @Test
    @DisplayName("수정: 제품이 없는 분류는 약어를 바꾼다")
    void updateAbbr() {
        given(categoryRepository.findById(2L)).willReturn(Optional.of(kitchen));
        given(categoryRepository.findByParentId(2L)).willReturn(List.of());
        given(productRepository.existsByCategoryIdIn(List.of(2L))).willReturn(false);

        categoryService.update(2L, new CategoryDto.UpdateRequest(null, "CK"));

        assertThat(kitchen.getAbbr()).isEqualTo("CK");
    }

    @Test
    @DisplayName("수정: 제품이 연결된 중분류의 약어는 CATEGORY_ABBR_LOCKED 로 막는다")
    void updateAbbrLockedMiddle() {
        given(categoryRepository.findById(11L)).willReturn(Optional.of(upright));
        given(productRepository.existsByCategoryIdIn(List.of(11L))).willReturn(true);

        assertErrorCode(() -> categoryService.update(11L, new CategoryDto.UpdateRequest(null, "UP")),
            ErrorCode.CATEGORY_ABBR_LOCKED);
        assertThat(upright.getAbbr()).isEqualTo("UR");
    }

    @Test
    @DisplayName("수정: 대분류는 아래 중분류에 제품이 있어도 약어를 못 바꾼다")
    void updateAbbrLockedTop() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(fridge));
        given(categoryRepository.findByParentId(1L)).willReturn(List.of(upright, table));
        given(productRepository.existsByCategoryIdIn(List.of(1L, 11L, 12L))).willReturn(true);

        assertErrorCode(() -> categoryService.update(1L, new CategoryDto.UpdateRequest(null, "FR")),
            ErrorCode.CATEGORY_ABBR_LOCKED);
    }

    @Test
    @DisplayName("수정: 제품이 연결돼 있어도 이름은 바뀌고, 지금과 같은 약어는 검사하지 않는다")
    void updateNameWhenLocked() {
        given(categoryRepository.findById(11L)).willReturn(Optional.of(upright));

        categoryService.update(11L, new CategoryDto.UpdateRequest("업소용 냉장고(대형)", "UR"));

        assertThat(upright.getName()).isEqualTo("업소용 냉장고(대형)");
        verify(productRepository, never()).existsByCategoryIdIn(any());
        verify(categoryRepository, never()).existsByParentIdAndAbbr(any(), anyString());
    }

    @Test
    @DisplayName("수정: 형제와 약어가 겹치면 CATEGORY_ABBR_DUPLICATE")
    void updateDuplicateAbbr() {
        given(categoryRepository.findById(2L)).willReturn(Optional.of(kitchen));
        given(categoryRepository.findByParentId(2L)).willReturn(List.of());
        given(categoryRepository.existsByParentIdAndAbbr(null, "RF")).willReturn(true);

        assertErrorCode(() -> categoryService.update(2L, new CategoryDto.UpdateRequest(null, "RF")),
            ErrorCode.CATEGORY_ABBR_DUPLICATE);
        assertThat(kitchen.getAbbr()).isEqualTo("KT");
    }

    @Test
    @DisplayName("수정: 없는 분류는 CATEGORY_NOT_FOUND")
    void updateNotFound() {
        given(categoryRepository.findById(anyLong())).willReturn(Optional.empty());

        assertErrorCode(() -> categoryService.update(999L, new CategoryDto.UpdateRequest("없음", null)),
            ErrorCode.CATEGORY_NOT_FOUND);
    }

    // ── 순서 ──

    @Test
    @DisplayName("순서: 배열 순서대로 1부터 다시 매긴다")
    void changeOrder() {
        given(categoryRepository.findByParentId(1L)).willReturn(List.of(upright, table));

        categoryService.changeOrder(new CategoryDto.ChangeOrderRequest(1L, List.of(12L, 11L)));

        assertThat(table.getSortOrder()).isEqualTo(1);
        assertThat(upright.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("순서: 형제가 빠졌거나·더해졌거나·겹치면 CATEGORY_ORDER_MISMATCH 이고 순서는 그대로")
    void changeOrderMismatch() {
        given(categoryRepository.findByParentId(null)).willReturn(List.of(fridge, kitchen));

        for (List<Long> ids : List.of(List.of(1L), List.of(1L, 2L, 11L), List.of(1L, 1L), List.of(1L, 11L))) {
            assertErrorCode(() -> categoryService.changeOrder(new CategoryDto.ChangeOrderRequest(null, ids)),
                ErrorCode.CATEGORY_ORDER_MISMATCH);
        }
        assertThat(fridge.getSortOrder()).isEqualTo(1);
        assertThat(kitchen.getSortOrder()).isEqualTo(2);
    }

    // ── 삭제 ──

    @Test
    @DisplayName("삭제: 하위 분류도 제품도 없으면 지운다")
    void delete() {
        given(categoryRepository.findById(2L)).willReturn(Optional.of(kitchen));
        given(categoryRepository.existsByParentId(2L)).willReturn(false);
        given(productRepository.existsByCategoryIdIn(List.of(2L))).willReturn(false);

        categoryService.delete(2L);

        verify(categoryRepository).delete(kitchen);
    }

    @Test
    @DisplayName("삭제: 중분류가 있는 대분류는 CATEGORY_IN_USE 로 막는다")
    void deleteTopWithChildren() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(fridge));
        given(categoryRepository.existsByParentId(1L)).willReturn(true);

        assertErrorCode(() -> categoryService.delete(1L), ErrorCode.CATEGORY_IN_USE);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 제품이 연결된 중분류는 CATEGORY_IN_USE 로 막는다")
    void deleteMiddleWithProducts() {
        given(categoryRepository.findById(11L)).willReturn(Optional.of(upright));
        given(categoryRepository.existsByParentId(11L)).willReturn(false);
        given(productRepository.existsByCategoryIdIn(List.of(11L))).willReturn(true);

        assertErrorCode(() -> categoryService.delete(11L), ErrorCode.CATEGORY_IN_USE);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 없는 분류는 CATEGORY_NOT_FOUND")
    void deleteNotFound() {
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertErrorCode(() -> categoryService.delete(999L), ErrorCode.CATEGORY_NOT_FOUND);
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }

    private Category category(Long id, Category parent, String name, String abbr, int sortOrder) {
        Category category = Category.create(parent, name, abbr, sortOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
