package com.swkitchen.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 기능을 만들 때 그 기능의 코드를 여기에 추가한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않습니다."),
    // 401 이면 화면이 로그인 만료로 보고 토큰을 새로 받으러 가므로 400 으로 둔다
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "분류를 찾을 수 없습니다."),
    // 분류는 대분류·중분류 두 단계뿐이다
    CATEGORY_NOT_MIDDLE(HttpStatus.BAD_REQUEST, "분류 단계가 맞지 않습니다."),
    CATEGORY_ABBR_DUPLICATE(HttpStatus.CONFLICT, "이미 쓰고 있는 약어입니다."),
    // 약어가 상품 코드 앞부분에 들어가므로 제품이 생긴 뒤에는 못 바꾼다
    // 화면이 연 뒤 다른 사람이 분류를 더하거나 지웠을 때도 난다. 화면은 다시 불러온다
    CATEGORY_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "분류 목록이 바뀌었습니다. 다시 불러와 주세요."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "하위 분류나 제품이 연결된 분류는 삭제할 수 없습니다."),
    CATEGORY_ABBR_LOCKED(HttpStatus.CONFLICT, "제품이 연결된 분류는 약어를 바꿀 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
