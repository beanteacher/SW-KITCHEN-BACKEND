package com.swkitchen.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @Embedded
    private Dimensions dimensions;

    @Embedded
    private Price price;

    private Integer stockQty;

    @Column(nullable = false)
    private int reservedQty;

    // 수정 화면을 연 사이 다른 곳에서 옵션이 바뀌었으면 저장을 거부한다
    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 100)
    private String mainImageKey;

    // 읽기용. 저장·삭제는 OptionImageRepository 로 하고, 이미지가 만들어지고 지워질 때 함께 맞춘다
    @OneToMany(mappedBy = "productOption")
    @OrderBy("sortOrder")
    private List<OptionImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 제품에서 옵션 번호를 하나 발급받고 제품의 옵션 목록에 들어간다 */
    public static ProductOption create(
            Product product,
            String modelName,
            Dimensions dimensions,
            Price price,
            Integer stockQty,
            String mainImageKey) {
        ProductOption option = new ProductOption();
        option.product = product;
        option.optionNo = product.nextOptionNo();
        option.productCode = "%s-%02d".formatted(product.getCodePrefix(), option.optionNo);
        option.change(modelName, dimensions, price, stockQty, mainImageKey);
        product.addOption(option);
        return option;
    }

    /** 손님에게 보이는 수량. 신제품은 재고를 세지 않아 null */
    public Integer visibleQty() {
        return stockQty == null ? null : stockQty - reservedQty;
    }

    /** 상품 코드·잡아 둔 수량은 바꾸지 않는다 */
    public void change(String modelName, Dimensions dimensions, Price price, Integer stockQty, String mainImageKey) {
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.price = price;
        this.stockQty = stockQty;
        this.mainImageKey = mainImageKey;
    }

    public List<OptionImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    /** 이미지를 통째로 지울 때 이 목록도 비운다 */
    public void clearImages() {
        images.clear();
    }

    // OptionImage.create 만 부른다
    void addImage(OptionImage image) {
        images.add(image);
    }
}
