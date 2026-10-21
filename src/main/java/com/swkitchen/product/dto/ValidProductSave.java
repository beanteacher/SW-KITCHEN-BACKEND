package com.swkitchen.product.dto;

import com.swkitchen.product.domain.SalesType;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * 제품 저장 요청에서 여러 칸을 함께 봐야 하는 검사. 오류는 칸 경로(예: {@code options[1].discountValue})로 붙인다.
 * 할인 값 범위, 재고품·중고품의 재고 필수, 기존 옵션의 version 필수.
 * 치수·사양 이름 겹침은 따로 오류 코드가 있어 서비스에서 본다
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidProductSave.Validator.class)
@interface ValidProductSave {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidProductSave, ProductDto.SaveRequest> {

        @Override
        public boolean isValid(ProductDto.SaveRequest request, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            boolean valid = true;

            List<ProductDto.OptionRequest> options = request.options() == null ? List.of() : request.options();
            boolean countsStock = request.salesType() != null && request.salesType() != SalesType.NEW;
            for (int i = 0; i < options.size(); i++) {
                ProductDto.OptionRequest option = options.get(i);
                if (option == null) {
                    continue;
                }
                String discountError = discountError(option);
                if (discountError != null) {
                    valid = reject(context, discountError, "options", i, "discountValue");
                }
                if (countsStock && option.stockQty() == null) {
                    valid = reject(context, "재고를 입력해 주세요.", "options", i, "stockQty");
                }
                if (option.id() != null && option.version() == null) {
                    valid = reject(context, "옵션 version 을 보내 주세요.", "options", i, "version");
                }
            }
            return valid;
        }

        // 할인 방식이 비었으면 그 칸 오류로 충분하다. 없음이면 값을 보지 않는다
        private static String discountError(ProductDto.OptionRequest option) {
            if (option.discountType() == null) {
                return null;
            }
            Integer value = option.discountValue();
            return switch (option.discountType()) {
                case NONE -> null;
                case RATE -> value == null || value < 1 || value > 99 ? "할인율은 1~99% 입니다." : null;
                case AMOUNT -> value == null || value < 1 || option.listPrice() != null && value >= option.listPrice()
                    ? "할인 금액은 1원 이상, 정상가보다 작아야 합니다." : null;
            };
        }

        private static boolean reject(ConstraintValidatorContext context, String message, String list, int index,
                String field) {
            context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(list)
                .addPropertyNode(field).inIterable().atIndex(index)
                .addConstraintViolation();
            return false;
        }
    }
}
