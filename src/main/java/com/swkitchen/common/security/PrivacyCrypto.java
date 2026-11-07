package com.swkitchen.common.security;

import org.springframework.stereotype.Component;

/** DB 에 넣는 개인정보(생년월일·휴대폰 번호)를 암호화한다 */
@Component
public class PrivacyCrypto {

    private final AesGcm aesGcm;

    public PrivacyCrypto(EncryptionProperties properties) {
        this.aesGcm = new AesGcm(properties.privacyKey());
    }

    public String encrypt(String plain) {
        return aesGcm.encrypt(plain);
    }

    public String decrypt(String encrypted) {
        return aesGcm.decrypt(encrypted);
    }
}
