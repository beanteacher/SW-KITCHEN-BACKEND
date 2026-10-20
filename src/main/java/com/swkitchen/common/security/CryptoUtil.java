package com.swkitchen.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 해시·랜덤 토큰·비교처럼 서비스 규칙과 상관없는 암호 작업을 모아 둔다 */
public final class CryptoUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoUtil() {
    }

    /** 주소창·쿠키에 그대로 실을 수 있는 랜덤 문자열 */
    public static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 앞에서 몇 글자가 맞았는지 걸린 시간으로 알 수 없게, 항상 끝까지 비교한다 */
    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
