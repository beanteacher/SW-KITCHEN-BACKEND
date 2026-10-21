package com.swkitchen.product.controller;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** X-Requested-With 헤더 검사는 SecurityConfig 에 있다 */
@RestController
@RequestMapping("/api/v1/admin/image")
@RequiredArgsConstructor
public class AdminImageController {

    private final ImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductDto.ImageResponse>> upload(@RequestPart("file") MultipartFile file) {
        ProductDto.ImageResponse image = imageService.upload(file);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(image));
    }
}
