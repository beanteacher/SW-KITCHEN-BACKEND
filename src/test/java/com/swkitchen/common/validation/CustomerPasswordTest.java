package com.swkitchen.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerPasswordTest {

    @Test
    @DisplayName("영문·숫자·특수문자를 모두 넣은 8자 이상만 받는다")
    void rule() {
        assertThat(CustomerPassword.Validator.isValid("abcd123!")).isTrue();
        assertThat(CustomerPassword.Validator.isValid("abcd1234")).isFalse();
        assertThat(CustomerPassword.Validator.isValid("ab1!")).isFalse();
        assertThat(CustomerPassword.Validator.isValid("abcdefg!")).isFalse();
        assertThat(CustomerPassword.Validator.isValid("1234567!")).isFalse();
    }

    @Test
    @DisplayName("공백·한글은 특수문자로 치지 않고, 72바이트를 넘으면 받지 않는다")
    void edge() {
        assertThat(CustomerPassword.Validator.isValid("abcd 1234")).isFalse();
        assertThat(CustomerPassword.Validator.isValid("abcd1234가")).isFalse();
        assertThat(CustomerPassword.Validator.isValid("a1!" + "b".repeat(69))).isTrue();
        assertThat(CustomerPassword.Validator.isValid("a1!" + "b".repeat(70))).isFalse();
    }
}
