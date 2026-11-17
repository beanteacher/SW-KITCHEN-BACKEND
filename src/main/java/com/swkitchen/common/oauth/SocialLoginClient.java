package com.swkitchen.common.oauth;

import java.util.Optional;

/** 카카오·네이버 로그인. 우리가 받는 값은 그 회원 번호뿐이다 (이름 등은 본인확인으로 받는다) */
public interface SocialLoginClient {

    String authorizeUrl(String state, String redirectUri);

    /** 받은 코드로 회원 번호를 얻는다. 실패하면 비어 있다 */
    Optional<String> fetchUserId(String code, String state, String redirectUri);
}
