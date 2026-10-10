package com.swkitchen.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.Material;
import com.swkitchen.product.domain.SalesType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSaveRequestValidationTest {

    static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    static final String KEY = "products/0a4e5c1e-1111-4222-8333-944455556666.jpg";

    @Test
    @DisplayName("올바른 요청은 오류가 없다")
    void valid() {
        assertThat(errors(request(SalesType.NEW, List.of(option(1200, null), option(1500, null)), specs("소비전력"))))
            .isEmpty();
    }

    @Test
    @DisplayName("옵션 칸이 비면 그 옵션 경로로 오류가 난다")
    void emptyOptionField() {
        ProductDto.OptionRequest noImage = new ProductDto.OptionRequest(null, null, null, 1200, 600, 800, 10_000,
            DiscountType.NONE, null, null, null, List.of());

        assertThat(errors(request(SalesType.NEW, List.of(option(1500, null), noImage), List.of())))
            .containsOnlyKeys("options[1].mainImageKey");
    }

    @Test
    @DisplayName("옵션이 없거나 100개면 거부한다")
    void optionCount() {
        List<ProductDto.OptionRequest> hundred = IntStream.rangeClosed(1, 100)
            .mapToObj(i -> option(i, null)).toList();

        assertThat(errors(request(SalesType.NEW, List.of(), List.of()))).containsOnlyKeys("options");
        assertThat(errors(request(SalesType.NEW, hundred, List.of()))).containsOnlyKeys("options");
    }

    @Test
    @DisplayName("이미지 키는 products/UUID.확장자 형식만 받는다")
    void imageKeyFormat() {
        ProductDto.OptionRequest badKey = new ProductDto.OptionRequest(null, null, null, 1200, 600, 800, 10_000,
            DiscountType.NONE, null, null, "../etc/passwd", List.of("products/abc.gif"));

        assertThat(errors(request(SalesType.NEW, List.of(badKey), List.of())))
            .containsOnlyKeys("options[0].mainImageKey", "options[0].imageKeys[0]");
    }

    @Test
    @DisplayName("정률 0·100, 정상가와 같은 정액은 거부한다")
    void discountRange() {
        List<ProductDto.OptionRequest> options = List.of(
            discounted(1, DiscountType.RATE, 0),
            discounted(2, DiscountType.RATE, 100),
            discounted(3, DiscountType.AMOUNT, 10_000),
            discounted(4, DiscountType.RATE, 17),
            discounted(5, DiscountType.AMOUNT, 9_999));

        assertThat(errors(request(SalesType.NEW, options, List.of()))).containsOnlyKeys(
            "options[0].discountValue", "options[1].discountValue", "options[2].discountValue");
    }

    @Test
    @DisplayName("재고품·중고품은 옵션마다 재고가 필요하고, 신제품은 재고가 없어도 된다")
    void stockBySalesType() {
        List<ProductDto.OptionRequest> options = List.of(option(1200, 3), option(1500, null));

        assertThat(errors(request(SalesType.STOCK, options, List.of()))).containsOnlyKeys("options[1].stockQty");
        assertThat(errors(request(SalesType.USED, options, List.of()))).containsOnlyKeys("options[1].stockQty");
        assertThat(errors(request(SalesType.NEW, options, List.of()))).isEmpty();
    }

    @Test
    @DisplayName("같은 치수의 옵션은 뒤에 나온 옵션에 오류가 난다")
    void duplicateSize() {
        assertThat(errors(request(SalesType.NEW, List.of(option(1200, null), option(1500, null), option(1200, null)),
            List.of()))).containsOnlyKeys("options[2].widthMm");
    }

    @Test
    @DisplayName("같은 이름의 사양은 뒤에 나온 줄에 오류가 난다")
    void duplicateSpecName() {
        assertThat(errors(request(SalesType.NEW, List.of(option(1200, null)), specs("소비전력", "용량", "소비전력 "))))
            .containsOnlyKeys("specs[2].name");
    }

    // 응답의 field 와 같게 맞춘다. Spring 은 목록 원소 표시(.<list element>)를 빼고 경로를 만든다
    private static Map<String, String> errors(ProductDto.SaveRequest request) {
        return validator.validate(request).stream()
            .collect(Collectors.toMap(v -> v.getPropertyPath().toString().replace(".<list element>", ""),
                ConstraintViolation::getMessage));
    }

    private static ProductDto.SaveRequest request(SalesType salesType, List<ProductDto.OptionRequest> options,
            List<ProductDto.SpecRequest> specs) {
        return new ProductDto.SaveRequest(12L, "업소용 냉장고", salesType, "우성", "국산", null,
            List.of(Material.STAINLESS), specs, List.of(KEY), List.of(), options);
    }

    private static ProductDto.OptionRequest option(int widthMm, Integer stockQty) {
        return new ProductDto.OptionRequest(null, null, null, widthMm, 600, 800, 10_000, DiscountType.NONE, null,
            stockQty, KEY, List.of());
    }

    private static ProductDto.OptionRequest discounted(int widthMm, DiscountType type, int value) {
        return new ProductDto.OptionRequest(null, null, null, widthMm, 600, 800, 10_000, type, value, null, KEY,
            List.of());
    }

    private static List<ProductDto.SpecRequest> specs(String... names) {
        return Arrays.stream(names).map(name -> new ProductDto.SpecRequest(name, "값")).toList();
    }
}
