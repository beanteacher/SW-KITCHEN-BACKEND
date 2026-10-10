package com.swkitchen.product.dto;

public class ProductDto {

    // ── 저장소 → 서비스 (조회 결과) ──

    public record CategoryProductCount(Long categoryId, long productCount) {
    }
}
