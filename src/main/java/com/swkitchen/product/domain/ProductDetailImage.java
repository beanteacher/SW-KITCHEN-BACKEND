package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품설명 아래에 순서대로 이어 붙이는 상세 이미지 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDetailImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String imageKey;

    @Column(nullable = false)
    private int sortOrder;

    public static ProductDetailImage create(Product product, String imageKey, int sortOrder) {
        ProductDetailImage image = new ProductDetailImage();
        image.product = product;
        image.imageKey = imageKey;
        image.sortOrder = sortOrder;
        return image;
    }
}
