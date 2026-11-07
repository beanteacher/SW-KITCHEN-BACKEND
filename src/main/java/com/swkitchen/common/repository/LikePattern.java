package com.swkitchen.common.repository;

/** 검색어를 부분 일치 LIKE 패턴으로 바꾼다. 검색어 안의 %·_ 는 글자 그대로 찾는다 */
public final class LikePattern {

    // MySQL 은 문자열 안의 \ 를 따로 해석해서, LIKE 이스케이프 문자로 ! 를 쓴다
    public static final char ESCAPE = '!';

    private LikePattern() {
    }

    public static String contains(String keyword) {
        String escaped = keyword
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
