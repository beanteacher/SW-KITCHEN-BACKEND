package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
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
    @Column(nullable = false)
    private Long categoryId;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Product create(Long categoryId, String name, SalesType salesType, String manufacturer, String origin,
            String codePrefix) {
        Product product = new Product();
        product.categoryId = categoryId;
        product.name = name;
        product.salesType = salesType;
        product.manufacturer = manufacturer;
        product.origin = origin;
        product.codePrefix = codePrefix;
        return product;
    }
}
