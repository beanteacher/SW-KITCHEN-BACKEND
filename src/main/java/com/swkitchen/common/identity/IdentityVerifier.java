package com.swkitchen.common.identity;

import java.util.Optional;

/** 본인확인 업체에 결과를 직접 묻는다. 화면이 보낸 이름·번호는 믿지 않는다. 업체를 바꾸면 구현만 바꾼다 */
public interface IdentityVerifier {

    /** 인증을 마친 건이면 결과를, 없거나 마치지 않았거나 업체에 닿지 않으면 비어 있다 */
    Optional<VerifiedPerson> fetch(String identityVerificationId);
}
