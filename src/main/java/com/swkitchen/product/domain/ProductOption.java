package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 제품의 사이즈 하나. 상품 코드·가격·재고는 옵션마다 따로 둔다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, updatable = false, columnDefinition = "tinyint")
    private int optionNo;

    @Column(nullable = false, updatable = false, columnDefinition = "char(13)")
    private String productCode;

    @Column(length = 100)
    private String modelName;

    @Column(nullable = false)
    private int widthMm;

    @Column(nullable = false)
    private int depthMm;

    @Column(nullable = false)
    private int heightMm;

    @Column(nullable = false)
    private int listPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DiscountType discountType;

    private Integer discountValue;

    @Column(nullable = false)
    private int salePrice;

    private Integer stockQty;

    @Column(nullable = false)
    private int reservedQty;

    // 수정 화면을 연 사이 다른 곳에서 옵션이 바뀌었으면 저장을 거부한다
    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 100)
    private String mainImageKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 제품에서 옵션 번호를 하나 발급받는다 */
    public static ProductOption create(Product product, String modelName, int widthMm, int depthMm, int heightMm,
            int listPrice, DiscountType discountType, Integer discountValue, Integer stockQty, String mainImageKey) {
        ProductOption option = new ProductOption();
        option.product = product;
        option.optionNo = product.nextOptionNo();
        option.productCode = "%s-%02d".formatted(product.getCodePrefix(), option.optionNo);
        option.modelName = modelName;
        option.widthMm = widthMm;
        option.depthMm = depthMm;
        option.heightMm = heightMm;
        option.changePrice(listPrice, discountType, discountValue);
        option.stockQty = stockQty;
        option.mainImageKey = mainImageKey;
        return option;
    }

    public void changePrice(int listPrice, DiscountType discountType, Integer discountValue) {
        this.listPrice = listPrice;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.salePrice = salePrice(listPrice, discountType, discountValue);
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
