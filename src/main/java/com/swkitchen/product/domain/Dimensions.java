package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 옵션 치수(mm). 한 제품 안에서 같은 치수의 옵션은 둘 수 없다 */
@Embeddable
public record Dimensions(
        @Column(nullable = false) int widthMm,
        @Column(nullable = false) int depthMm,
        @Column(nullable = false) int heightMm) {
}
