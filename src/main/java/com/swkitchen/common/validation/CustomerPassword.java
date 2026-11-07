package com.swkitchen.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/** 고객 비밀번호: 8자 이상 72바이트 이하, 영문·숫자·특수문자(영문·숫자·공백 밖의 ASCII) 각 1자 이상. 비어 있으면 @NotEmpty 가 본다 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CustomerPassword.Validator.class)
public @interface CustomerPassword {

    String MESSAGE = "비밀번호는 영문·숫자·특수문자를 모두 넣어 8자 이상으로 정해 주세요.";

    String message() default MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CustomerPassword, String> {

        private static final int MIN_LENGTH = 8;
        private static final int MAX_BYTES = 72;

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isEmpty() || isValid(value);
        }

        public static boolean isValid(String value) {
            return value.length() >= MIN_LENGTH
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && value.chars().anyMatch(c -> c < 128 && Character.isLetter(c))
                && value.chars().anyMatch(c -> c >= '0' && c <= '9')
                && value.chars().anyMatch(c -> c > ' ' && c < 127 && !Character.isLetterOrDigit(c));
        }
    }
}
