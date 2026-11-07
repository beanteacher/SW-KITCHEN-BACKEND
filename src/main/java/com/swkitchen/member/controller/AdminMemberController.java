package com.swkitchen.member.controller;

import com.swkitchen.common.dto.ApiResponse;
import com.swkitchen.common.dto.PageResponse;
import com.swkitchen.member.dto.MemberDto;
import com.swkitchen.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/member")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MemberDto.AdminSummaryResponse>>> search(
            @Valid MemberDto.AdminSearchRequest request,
            Pageable pageable) {
        PageResponse<MemberDto.AdminSummaryResponse> members = memberService.searchAdmin(request, pageable);

        return ResponseEntity.ok(ApiResponse.success(members));
    }
}
