package com.swkitchen.member.service;

import com.swkitchen.common.dto.PageResponse;
import com.swkitchen.member.dto.MemberDto;
import com.swkitchen.member.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final AccountRepository accountRepository;

    public PageResponse<MemberDto.AdminSummaryResponse> searchAdmin(MemberDto.AdminSearchRequest request, Pageable pageable) {
        return PageResponse.from(
            accountRepository.searchCustomers(request.keyword(), pageable).map(MemberDto.AdminSummaryResponse::from));
    }
}
