package com.swkitchen.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmTest {

    static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    AesGcm aesGcm = new AesGcm(KEY);

    @Test
    @DisplayName("암호화한 값을 풀면 원래 값이고, 같은 값도 매번 다른 암호문이 된다")
    void roundTrip() {
        String first = aesGcm.encrypt("01012345678");
        String second = aesGcm.encrypt("01012345678");

        assertThat(aesGcm.decrypt(first)).isEqualTo("01012345678");
        assertThat(first).isNotEqualTo(second).doesNotContain("01012345678");
    }

    @Test
    @DisplayName("한 글자라도 고친 암호문이나 다른 키로 만든 암호문은 풀리지 않는다")
    void tampered() {
        String encrypted = aesGcm.encrypt("01012345678");
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> aesGcm.decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AesGcm(OTHER_KEY).decrypt(encrypted)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aesGcm.decrypt("short")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 만들 때 실패한다")
    void wrongKeySize() {
        assertThatThrownBy(() -> new AesGcm(Base64.getEncoder().encodeToString("short".getBytes())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
