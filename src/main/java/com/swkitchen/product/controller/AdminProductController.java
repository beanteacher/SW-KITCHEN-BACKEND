package com.swkitchen.product.controller;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto.AdminResponse>> get(@PathVariable Long id) {
        ProductDto.AdminResponse product = productService.getAdmin(id);

        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDto.AdminResponse>> create(@Valid @RequestBody ProductDto.SaveRequest request) {
        ProductDto.AdminResponse product = productService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(product));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDto.AdminResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductDto.SaveRequest request) {
        ProductDto.AdminResponse product = productService.update(id, request);

        return ResponseEntity.ok(ApiResponse.success(product));
    }
}
