package com.swkitchen.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 값을 더하면 ck_product_material 을 바꾸는 마이그레이션도 같이 배포한다 */
@Getter
@RequiredArgsConstructor
public enum Material {

    STAINLESS("스테인리스"),
    ALUMINUM("알루미늄"),
    STEEL("철"),
    PLASTIC("플라스틱");

    private final String displayName;
}
