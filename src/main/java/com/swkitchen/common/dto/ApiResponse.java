package com.swkitchen.common.dto;

import java.time.OffsetDateTime;

/** 성공 응답. 실패는 {@link ErrorResponse}. */
public record ApiResponse<T>(boolean success, T data, String message, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now());
    }
}
