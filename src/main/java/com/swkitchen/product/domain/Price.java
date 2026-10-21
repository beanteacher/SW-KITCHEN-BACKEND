package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정상가와 할인. 판매가는 여기서 계산해 함께 저장한다 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Price {

    @Column(nullable = false)
    private int listPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DiscountType discountType;

    private Integer discountValue;

    @Column(nullable = false)
    private int salePrice;

    public static Price of(int listPrice, DiscountType discountType, Integer discountValue) {
        Price price = new Price();
        price.listPrice = listPrice;
        price.discountType = discountType;
        // 할인 없음이면 화면에 남아 있던 값을 버린다
        price.discountValue = discountType == DiscountType.NONE ? null : discountValue;
        price.salePrice = salePrice(listPrice, discountType, discountValue);
        return price;
    }

    /** 정률은 100원 미만을 버린다 */
    static int salePrice(int listPrice, DiscountType discountType, Integer discountValue) {
        return switch (discountType) {
            case NONE -> listPrice;
            case RATE -> (int) ((long) listPrice * (100 - discountValue) / 10_000 * 100);
            case AMOUNT -> listPrice - discountValue;
        };
    }
}
