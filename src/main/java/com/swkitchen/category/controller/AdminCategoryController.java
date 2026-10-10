package com.swkitchen.category.controller;

import com.swkitchen.category.dto.CategoryDto;
import com.swkitchen.category.service.CategoryService;
import com.swkitchen.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryDto.AdminTreeResponse>>> getTree() {
        List<CategoryDto.AdminTreeResponse> tree = categoryService.getAdminTree();

        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CategoryDto.Response>> create(@Valid @RequestBody CategoryDto.CreateRequest request) {
        CategoryDto.Response category = categoryService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(category));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CategoryDto.Response>> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryDto.UpdateRequest request) {
        CategoryDto.Response category = categoryService.update(id, request);

        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PutMapping(value = "/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> changeOrder(@Valid @RequestBody CategoryDto.ChangeOrderRequest request) {
        categoryService.changeOrder(request);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
