package com.swkitchen.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 페이지 목록 응답. page 는 요청과 같이 1부터 센다 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber() + 1,
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }
}
