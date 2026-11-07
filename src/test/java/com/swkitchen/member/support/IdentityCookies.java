package com.swkitchen.member.support;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.swkitchen.common.identity.IdentityVerifier;
import com.swkitchen.common.identity.VerifiedPerson;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 가짜 업체 결과로 본인확인 API 를 불러 verified_identity 쿠키를 받는다 */
public final class IdentityCookies {

    private IdentityCookies() {
    }

    public static Cookie verify(MockMvc mvc, IdentityVerifier verifier, String ci, String purpose) throws Exception {
        String id = "iv-" + UUID.randomUUID();
        VerifiedPerson person = MemberFixtures.person(id, ci);
        given(verifier.fetch(id)).willReturn(Optional.of(person));
        return mvc.perform(post("/api/v1/identity-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityVerificationId\":\"" + id + "\",\"purpose\":\"" + purpose + "\"}"))
            .andReturn().getResponse().getCookie("verified_identity");
    }
}
