package com.swkitchen.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null 이면 대분류, 있으면 중분류
    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, columnDefinition = "char(2)")
    private String abbr;

    @Column(nullable = false)
    private int sortOrder;

    public static Category create(Long parentId, String name, String abbr, int sortOrder) {
        Category category = new Category();
        category.parentId = parentId;
        category.name = name;
        category.abbr = abbr;
        category.sortOrder = sortOrder;
        return category;
    }

    public boolean isTop() {
        return parentId == null;
    }
}
