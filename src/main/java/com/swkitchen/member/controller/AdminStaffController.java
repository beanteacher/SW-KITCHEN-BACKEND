package com.swkitchen.member.controller;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.member.dto.StaffDto;
import com.swkitchen.member.service.StaffService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staff")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffDto.Response>>> getAll() {
        List<StaffDto.Response> staff = staffService.getAll();

        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StaffDto.Response>> create(@Valid @RequestBody StaffDto.CreateRequest request) {
        StaffDto.Response staff = staffService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StaffDto.Response>> update(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody StaffDto.UpdateRequest request) {
        StaffDto.Response staff = staffService.update(adminId, id, request);

        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @PutMapping(value = "/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody StaffDto.PasswordRequest request) {
        staffService.resetPassword(id, request);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
