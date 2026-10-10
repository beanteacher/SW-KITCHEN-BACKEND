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

/** 옵션 추가 이미지. 대표 이미지는 ProductOption.mainImageKey */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_option_id", nullable = false, updatable = false)
    private ProductOption productOption;

    @Column(nullable = false, length = 100)
    private String imageKey;

    @Column(nullable = false)
    private int sortOrder;

    public static OptionImage create(ProductOption productOption, String imageKey, int sortOrder) {
        OptionImage image = new OptionImage();
        image.productOption = productOption;
        image.imageKey = imageKey;
        image.sortOrder = sortOrder;
        return image;
    }
}
