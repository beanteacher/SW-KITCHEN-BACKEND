package com.swkitchen.category.controller;

import com.swkitchen.category.dto.CategoryDto;
import com.swkitchen.category.service.CategoryService;
import com.swkitchen.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryDto.AdminTreeResponse>>> getTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAdminTree()));
    }
}
