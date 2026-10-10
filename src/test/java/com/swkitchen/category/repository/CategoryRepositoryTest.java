package com.swkitchen.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.category.domain.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@RepositoryTest
class CategoryRepositoryTest {

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    @DisplayName("대분류 아래에 중분류를 만든다")
    void createChild() {
        Category top = categoryRepository.saveAndFlush(Category.create(null, "냉장·냉동", "RF", 1));
        Category child = categoryRepository.saveAndFlush(Category.create(top.getId(), "업소용 냉장고", "UR", 1));

        assertThat(top.isTop()).isTrue();
        assertThat(child.getParentId()).isEqualTo(top.getId());
    }

    @Test
    @DisplayName("대분류끼리 약어가 겹치면 DB 가 막는다")
    void duplicateTopAbbr() {
        categoryRepository.saveAndFlush(Category.create(null, "냉장·냉동", "RF", 1));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.create(null, "냉장고", "RF", 2)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 대분류 안 중분류끼리 약어가 겹치면 DB 가 막는다")
    void duplicateChildAbbr() {
        Category top = categoryRepository.saveAndFlush(Category.create(null, "냉장·냉동", "RF", 1));
        categoryRepository.saveAndFlush(Category.create(top.getId(), "업소용 냉장고", "UR", 1));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.create(top.getId(), "업소용 냉동고", "UR", 2)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 대분류 아래라면 중분류 약어가 같아도 된다")
    void sameAbbrUnderOtherParent() {
        Category fridge = categoryRepository.saveAndFlush(Category.create(null, "냉장·냉동", "RF", 1));
        Category table = categoryRepository.saveAndFlush(Category.create(null, "작업대·싱크", "WS", 2));
        categoryRepository.saveAndFlush(Category.create(fridge.getId(), "업소용 냉장고", "UR", 1));

        assertThat(categoryRepository.saveAndFlush(Category.create(table.getId(), "업소용 싱크", "UR", 1)).getId())
            .isNotNull();
    }

    @Test
    @DisplayName("약어는 대문자 두 글자만 DB 가 받는다")
    void abbrCheck() {
        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.create(null, "냉장", "rf", 1)))
            .rootCause().hasMessageContaining("ck_category_abbr");
    }

    @Test
    @DisplayName("없는 부모 아래에는 만들 수 없다")
    void parentMustExist() {
        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.create(999_999L, "업소용 냉장고", "UR", 1)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("부모가 null 이면 대분류끼리만 보고 약어 중복·마지막 순서를 찾는다")
    void findByNullParent() {
        Category top = categoryRepository.save(Category.create(null, "냉장·냉동", "RF", 1));
        categoryRepository.save(Category.create(null, "조리", "KT", 2));
        categoryRepository.save(Category.create(top.getId(), "업소용 냉장고", "UR", 5));

        assertThat(categoryRepository.existsByParentIdAndAbbr(null, "RF")).isTrue();
        assertThat(categoryRepository.existsByParentIdAndAbbr(null, "UR")).isFalse();
        assertThat(categoryRepository.findTopByParentIdOrderBySortOrderDesc(null)).get()
            .extracting(Category::getAbbr).isEqualTo("KT");
        assertThat(categoryRepository.findTopByParentIdOrderBySortOrderDesc(top.getId())).get()
            .extracting(Category::getAbbr).isEqualTo("UR");
    }

    @Test
    @DisplayName("하위 분류가 있는지 본다")
    void existsByParentId() {
        Category top = categoryRepository.save(Category.create(null, "냉장·냉동", "RF", 1));
        Category empty = categoryRepository.save(Category.create(null, "조리", "KT", 2));
        categoryRepository.save(Category.create(top.getId(), "업소용 냉장고", "UR", 1));

        assertThat(categoryRepository.existsByParentId(top.getId())).isTrue();
        assertThat(categoryRepository.existsByParentId(empty.getId())).isFalse();
    }
}
