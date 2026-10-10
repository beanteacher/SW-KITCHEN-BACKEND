package com.swkitchen.product.domain;

import com.swkitchen.category.domain.Category;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 중분류만 가리킨다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SalesType salesType;

    @Column(nullable = false, length = 100)
    private String manufacturer;

    @Column(nullable = false, length = 50)
    private String origin;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, updatable = false, columnDefinition = "char(10)")
    private String codePrefix;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int lastOptionNo;

    @ElementCollection
    @CollectionTable(name = "product_material", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "material", nullable = false, length = 20)
    private Set<Material> materials = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Product create(Category category, String name, SalesType salesType, String manufacturer, String origin,
            String codePrefix) {
        Product product = new Product();
        product.category = category;
        product.name = name;
        product.salesType = salesType;
        product.manufacturer = manufacturer;
        product.origin = origin;
        product.codePrefix = codePrefix;
        return product;
    }

    /** 지운 옵션의 번호는 다시 쓰지 않는다 */
    public int nextOptionNo() {
        return ++lastOptionNo;
    }

    public void changeMaterials(Collection<Material> materials) {
        this.materials.clear();
        this.materials.addAll(materials);
    }

    public Set<Material> getMaterials() {
        return Collections.unmodifiableSet(materials);
    }
}
