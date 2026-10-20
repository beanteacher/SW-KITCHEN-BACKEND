package com.swkitchen.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // 읽기용. 저장·삭제는 CategoryRepository 로 한다
    @OneToMany(mappedBy = "parent")
    @OrderBy("sortOrder ASC, id ASC")
    private List<Category> children = new ArrayList<>();

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, columnDefinition = "char(2)")
    private String abbr;

    @Column(nullable = false)
    private int sortOrder;

    public static Category create(Category parent, String name, String abbr, int sortOrder) {
        Category category = new Category();
        category.parent = parent;
        category.name = name;
        category.abbr = abbr;
        category.sortOrder = sortOrder;
        if (parent != null) {
            parent.addChild(category);
        }
        return category;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeAbbr(String abbr) {
        this.abbr = abbr;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<Category> getChildren() {
        return Collections.unmodifiableList(children);
    }

    void addChild(Category child) {
        children.add(child);
    }

    public void removeChild(Category child) {
        children.remove(child);
    }

    public boolean isTop() {
        return parent == null;
    }
}
