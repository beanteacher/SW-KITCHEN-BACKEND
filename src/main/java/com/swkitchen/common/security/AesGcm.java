package com.swkitchen.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM. 결과는 base64url(IV 12바이트 + 암호문 + 태그) 이다. 고쳐진 값은 풀 때 실패한다 */
public final class AesGcm {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /** 키는 base64 로 적은 32바이트다. 잘못 넣으면 켜질 때 바로 실패한다 */
    public AesGcm(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key);
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("암호화 키는 32바이트여야 합니다");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 고쳐졌거나 다른 키로 만든 값이면 IllegalArgumentException */
    public String decrypt(String encoded) {
        try {
            byte[] in = Base64.getUrlDecoder().decode(encoded);
            if (in.length <= IV_BYTES) {
                throw new IllegalArgumentException("암호문이 너무 짧습니다");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("암호문을 풀 수 없습니다", e);
        }
    }
}
