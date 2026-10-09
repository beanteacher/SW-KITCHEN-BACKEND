package com.swkitchen.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** 실패 응답. {@code errors} 는 항목별 오류가 있을 때만 준다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(boolean success, String code, String message, List<FieldError> errors,
                            OffsetDateTime timestamp) {

    public static ErrorResponse of(String code, String message, List<FieldError> errors) {
        return new ErrorResponse(false, code, message, errors, OffsetDateTime.now());
    }

    public record FieldError(String field, String message) {}
}
