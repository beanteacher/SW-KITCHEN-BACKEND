package com.swkitchen.common.exception;

import com.swkitchen.common.dto.ErrorResponse;
import java.util.List;
import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    // 특정 칸·줄이 문제일 때만 담는다. 없으면 응답에 errors 가 빠진다
    private final List<ErrorResponse.FieldError> errors;

    public AppException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public AppException(ErrorCode errorCode, List<ErrorResponse.FieldError> errors) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.errors = errors;
    }
}
