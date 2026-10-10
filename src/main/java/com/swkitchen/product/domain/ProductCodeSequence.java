package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 접두어(예: RFUR)마다 마지막으로 쓴 제품 번호 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCodeSequence {

    @Id
    @Column(columnDefinition = "char(4)")
    private String prefix;

    @Column(nullable = false)
    private int lastNo;

    public static ProductCodeSequence create(String prefix) {
        ProductCodeSequence sequence = new ProductCodeSequence();
        sequence.prefix = prefix;
        return sequence;
    }

    /** 다음 번호로 제품 코드 앞부분을 만든다. 예: RFUR-00124 */
    public String issue() {
        return "%s-%05d".formatted(prefix, ++lastNo);
    }
}
