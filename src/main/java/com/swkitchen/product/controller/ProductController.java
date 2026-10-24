package com.swkitchen.product.controller;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.dto.PageResponse;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 고객용. 로그인 없이 열린다 (공개 경로는 SecurityConfig) */
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductDto.SummaryResponse>>> search(
            @Valid ProductDto.SearchRequest request,
            Pageable pageable) {
        PageResponse<ProductDto.SummaryResponse> products = productService.search(request, pageable);

        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<ProductDto.FilterResponse>> getFilter(@Valid ProductDto.SearchRequest request) {
        ProductDto.FilterResponse filter = productService.getFilter(request);

        return ResponseEntity.ok(ApiResponse.success(filter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto.DetailResponse>> get(@PathVariable Long id) {
        ProductDto.DetailResponse product = productService.get(id);

        return ResponseEntity.ok(ApiResponse.success(product));
    }
}
