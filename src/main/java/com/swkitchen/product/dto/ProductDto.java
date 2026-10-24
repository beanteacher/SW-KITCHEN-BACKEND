package com.swkitchen.product.dto;

import com.swkitchen.category.domain.Category;
import com.swkitchen.product.domain.Dimensions;
import com.swkitchen.product.domain.DiscountType;
import com.swkitchen.product.domain.Material;
import com.swkitchen.product.domain.Price;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductOption;
import com.swkitchen.product.domain.ProductSpec;
import com.swkitchen.product.domain.SalesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import org.hibernate.validator.constraints.Range;

public class ProductDto {

    private static final String IMAGE_KEY = "^products/[0-9a-f-]{36}\\.(jpg|png|webp)$";
    private static final String IMAGE_KEY_MESSAGE = "이미지 키 형식이 맞지 않습니다.";

    // ── API 요청·응답 (Request / Response) ──

    // 등록·전체 수정 같은 본문. 상품 코드·판매가·잡아 둔 수량은 서버가 만들어 받지 않는다
    // 배열 순서가 표시 순서다
    @ValidProductSave
    public record SaveRequest(
            @NotNull(message = "분류를 골라 주세요.") Long categoryId,
            @NotBlank(message = "제품명을 입력해 주세요.") @Size(max = 100, message = "제품명은 100자 이하입니다.") String name,
            @NotNull(message = "판매 구분을 골라 주세요.") SalesType salesType,
            @NotBlank(message = "제조사를 입력해 주세요.") @Size(max = 50, message = "제조사는 50자 이하입니다.") String manufacturer,
            @NotBlank(message = "원산지를 입력해 주세요.") @Size(max = 30, message = "원산지는 30자 이하입니다.") String origin,
            @Size(max = 5000, message = "설명은 5,000자 이하입니다.") String description,
            List<@NotNull(message = "재질을 골라 주세요.") Material> materials,
            @Size(max = 30, message = "사양은 30줄까지입니다.")
            List<@NotNull(message = "사양이 비었습니다.") @Valid SpecRequest> specs,
            @Size(max = 10, message = "추가 이미지는 10장까지입니다.")
            List<@NotNull(message = IMAGE_KEY_MESSAGE) @Pattern(regexp = IMAGE_KEY, message = IMAGE_KEY_MESSAGE) String> imageKeys,
            @Size(max = 20, message = "상세 이미지는 20장까지입니다.")
            List<@NotNull(message = IMAGE_KEY_MESSAGE) @Pattern(regexp = IMAGE_KEY, message = IMAGE_KEY_MESSAGE) String> detailImageKeys,
            @NotEmpty(message = "옵션을 1개 이상 넣어 주세요.") @Size(max = 99, message = "옵션은 99개까지입니다.")
            List<@NotNull(message = "옵션이 비었습니다.") @Valid OptionRequest> options) {
    }

    public record SpecRequest(
            @NotBlank(message = "사양 이름을 입력해 주세요.") @Size(max = 30, message = "사양 이름은 30자 이하입니다.") String name,
            @NotBlank(message = "사양 값을 입력해 주세요.") @Size(max = 100, message = "사양 값은 100자 이하입니다.") String value) {
    }

    // id·version 은 수정할 때 기존 옵션을 가리킨다. 등록에서는 비운다
    // stockQty 는 신제품이면 무시한다
    public record OptionRequest(
            Long id,
            Integer version,
            @Size(max = 50, message = "모델명은 50자 이하입니다.") String modelName,
            @NotNull(message = "가로를 입력해 주세요.") @Range(min = 1, max = 20_000, message = "가로는 1~20,000mm 입니다.") Integer widthMm,
            @NotNull(message = "세로를 입력해 주세요.") @Range(min = 1, max = 20_000, message = "세로는 1~20,000mm 입니다.") Integer depthMm,
            @NotNull(message = "높이를 입력해 주세요.") @Range(min = 1, max = 20_000, message = "높이는 1~20,000mm 입니다.") Integer heightMm,
            @NotNull(message = "정상가를 입력해 주세요.")
            @Range(min = 1, max = 999_999_999, message = "정상가는 1~999,999,999원입니다.") Integer listPrice,
            @NotNull(message = "할인 방식을 골라 주세요.") DiscountType discountType,
            Integer discountValue,
            @Range(max = 99_999, message = "재고는 0~99,999개입니다.") Integer stockQty,
            @NotNull(message = "대표 이미지를 올려 주세요.") @Pattern(regexp = IMAGE_KEY, message = IMAGE_KEY_MESSAGE) String mainImageKey,
            @Size(max = 10, message = "옵션 추가 이미지는 10장까지입니다.")
            List<@NotNull(message = IMAGE_KEY_MESSAGE) @Pattern(regexp = IMAGE_KEY, message = IMAGE_KEY_MESSAGE) String> imageKeys) {

        public Dimensions toDimensions() {
            return new Dimensions(widthMm, depthMm, heightMm);
        }

        public Price toPrice() {
            return Price.of(listPrice, discountType, discountValue);
        }
    }

    // 관리 화면(등록·수정)용 전체 데이터. 고객용 상세에 모델명·재고·잡아 둔 수량·version·이미지 키를 더한다
    // 신제품은 stockQty 가 null
    public record AdminResponse(
            Long id,
            String name,
            SalesType salesType,
            CategoryRef category,
            String manufacturer,
            String origin,
            List<MaterialResponse> materials,
            List<SpecResponse> specs,
            String description,
            List<ImageResponse> images,
            List<ImageResponse> detailImages,
            List<AdminOptionResponse> options) {

        /** urlOf 는 이미지 키에 저장소 주소를 붙인다 */
        public static AdminResponse of(Product product, UnaryOperator<String> urlOf) {
            List<MaterialResponse> materialResponses = product.getMaterials().stream()
                .sorted()
                .map(MaterialResponse::from)
                .toList();
            List<SpecResponse> specResponses = product.getSpecs().stream()
                .map(SpecResponse::from)
                .toList();
            List<ImageResponse> imageResponses = product.getImages().stream()
                .map(image -> ImageResponse.of(image.getImageKey(), urlOf))
                .toList();
            List<ImageResponse> detailImageResponses = product.getDetailImages().stream()
                .map(image -> ImageResponse.of(image.getImageKey(), urlOf))
                .toList();
            List<AdminOptionResponse> optionResponses = product.getOptions().stream()
                .map(option -> AdminOptionResponse.of(option, urlOf))
                .toList();

            return new AdminResponse(
                product.getId(),
                product.getName(),
                product.getSalesType(),
                CategoryRef.from(product.getCategory()),
                product.getManufacturer(),
                product.getOrigin(),
                materialResponses,
                specResponses,
                product.getDescription(),
                imageResponses,
                detailImageResponses,
                optionResponses);
        }
    }

    // 고객 목록·검색 조건 (SCR-001~003). 모두 비우면 전체 제품이다. 검색어는 앞뒤 공백을 지우고, 비면 조건에서 뺀다
    public record SearchRequest(
            List<SalesType> salesType,
            Long categoryId,
            List<Material> materials,
            @Size(max = 50, message = "검색어는 50자 이하입니다.") String keyword,
            OrderBy orderBy) {

        public SearchRequest {
            keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
            orderBy = orderBy == null ? OrderBy.POPULAR : orderBy;
        }
    }

    // 같은 값이면 신규 등록순. 인기순은 주문 기능 전까지 신규 등록순과 같다 (C3 4장)
    public enum OrderBy {
        POPULAR, NEWEST, PRICE_ASC, PRICE_DESC
    }

    // 목록 카드 한 장. 대표 이미지는 첫 번째 옵션 것, 가격은 판매가가 가장 낮은 옵션 것(같으면 앞 번호)
    // 신제품은 visibleQty 가 null
    public record SummaryResponse(
            Long id,
            String name,
            SalesType salesType,
            String mainImageUrl,
            PriceResponse price,
            Integer visibleQty) {

        public static SummaryResponse of(Product product, UnaryOperator<String> urlOf) {
            List<ProductOption> options = product.getOptions();
            ProductOption cheapest = options.stream()
                .min(Comparator.comparingInt((ProductOption option) -> option.getPrice().getSalePrice())
                    .thenComparingInt(ProductOption::getOptionNo))
                .orElseThrow();
            boolean varies = options.stream()
                .map(option -> option.getPrice().getSalePrice())
                .distinct()
                .count() > 1;
            Integer visibleQty = product.getSalesType() == SalesType.NEW
                ? null
                : options.stream().mapToInt(ProductOption::visibleQty).sum();

            return new SummaryResponse(
                product.getId(),
                product.getName(),
                product.getSalesType(),
                urlOf.apply(options.get(0).getMainImageKey()),
                PriceResponse.of(cheapest.getPrice(), varies),
                visibleQty);
        }
    }

    // varies 는 옵션마다 판매가가 달라 화면이 "~" 를 붙여야 하는지
    public record PriceResponse(
            int salePrice,
            int listPrice,
            DiscountType discountType,
            Integer discountValue,
            boolean varies) {

        public static PriceResponse of(Price price, boolean varies) {
            return new PriceResponse(
                price.getSalePrice(),
                price.getListPrice(),
                price.getDiscountType(),
                price.getDiscountValue(),
                varies);
        }
    }

    // 고객용 상세 (SCR-004). 모델명·재고·잡아 둔 수량은 넣지 않고 보이는 수량만 준다
    public record DetailResponse(
            Long id,
            String name,
            SalesType salesType,
            CategoryRef category,
            String manufacturer,
            String origin,
            List<MaterialResponse> materials,
            List<SpecResponse> specs,
            String description,
            List<String> imageUrls,
            List<String> detailImageUrls,
            List<OptionResponse> options) {

        public static DetailResponse of(Product product, UnaryOperator<String> urlOf) {
            List<MaterialResponse> materialResponses = product.getMaterials().stream()
                .sorted()
                .map(MaterialResponse::from)
                .toList();
            List<SpecResponse> specResponses = product.getSpecs().stream()
                .map(SpecResponse::from)
                .toList();
            List<String> imageUrls = product.getImages().stream()
                .map(image -> urlOf.apply(image.getImageKey()))
                .toList();
            List<String> detailImageUrls = product.getDetailImages().stream()
                .map(image -> urlOf.apply(image.getImageKey()))
                .toList();
            List<OptionResponse> optionResponses = product.getOptions().stream()
                .map(option -> OptionResponse.of(option, urlOf))
                .toList();

            return new DetailResponse(
                product.getId(),
                product.getName(),
                product.getSalesType(),
                CategoryRef.from(product.getCategory()),
                product.getManufacturer(),
                product.getOrigin(),
                materialResponses,
                specResponses,
                product.getDescription(),
                imageUrls,
                detailImageUrls,
                optionResponses);
        }
    }

    // 보이는 수량이 0 인 옵션도 준다. 화면이 품절로 보여준다. 신제품은 visibleQty 가 null
    public record OptionResponse(
            Long id,
            String productCode,
            int widthMm,
            int depthMm,
            int heightMm,
            int listPrice,
            DiscountType discountType,
            Integer discountValue,
            int salePrice,
            Integer visibleQty,
            String mainImageUrl,
            List<String> imageUrls) {

        public static OptionResponse of(ProductOption option, UnaryOperator<String> urlOf) {
            Dimensions dimensions = option.getDimensions();
            Price price = option.getPrice();
            List<String> imageUrls = option.getImages().stream()
                .map(image -> urlOf.apply(image.getImageKey()))
                .toList();

            return new OptionResponse(
                option.getId(),
                option.getProductCode(),
                dimensions.widthMm(),
                dimensions.depthMm(),
                dimensions.heightMm(),
                price.getListPrice(),
                price.getDiscountType(),
                price.getDiscountValue(),
                price.getSalePrice(),
                option.visibleQty(),
                urlOf.apply(option.getMainImageKey()),
                imageUrls);
        }
    }

    // 관리 목록 검색 조건. 모두 비우면 전체 제품이다. 검색어는 앞뒤 공백을 지우고, 비면 조건에서 뺀다
    public record AdminSearchRequest(
            @Size(max = 50, message = "검색어는 50자 이하입니다.") String keyword,
            Long categoryId,
            List<SalesType> salesType) {

        public AdminSearchRequest {
            keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        }
    }

    // 관리 목록 한 줄. categoryName 은 "대분류 > 중분류"
    public record AdminSummaryResponse(
            Long id,
            String name,
            SalesType salesType,
            String categoryName,
            String manufacturer,
            List<AdminSummaryOptionResponse> options) {

        public static AdminSummaryResponse from(Product product) {
            Category category = product.getCategory();
            List<AdminSummaryOptionResponse> optionResponses = product.getOptions().stream()
                .map(AdminSummaryOptionResponse::from)
                .toList();

            return new AdminSummaryResponse(
                product.getId(),
                product.getName(),
                product.getSalesType(),
                category.getParent().getName() + " > " + category.getName(),
                product.getManufacturer(),
                optionResponses);
        }
    }

    // 신제품은 stockQty·visibleQty 가 null
    public record AdminSummaryOptionResponse(
            Long id,
            String productCode,
            String modelName,
            int salePrice,
            Integer stockQty,
            int reservedQty,
            Integer visibleQty) {

        public static AdminSummaryOptionResponse from(ProductOption option) {
            return new AdminSummaryOptionResponse(
                option.getId(),
                option.getProductCode(),
                option.getModelName(),
                option.getPrice().getSalePrice(),
                option.getStockQty(),
                option.getReservedQty(),
                option.visibleQty());
        }
    }

    public record CategoryRef(Long id, String name, CategoryRef parent) {

        public static CategoryRef from(Category category) {
            return new CategoryRef(
                category.getId(),
                category.getName(),
                category.isTop() ? null : from(category.getParent()));
        }
    }

    public record MaterialResponse(Material code, String name) {

        public static MaterialResponse from(Material material) {
            return new MaterialResponse(material, material.getDisplayName());
        }
    }

    public record SpecResponse(String name, String value) {

        public static SpecResponse from(ProductSpec spec) {
            return new SpecResponse(spec.getName(), spec.getValue());
        }
    }

    public record ImageResponse(String key, String url) {

        public static ImageResponse of(String key, UnaryOperator<String> urlOf) {
            return new ImageResponse(key, urlOf.apply(key));
        }
    }

    public record AdminOptionResponse(
            Long id,
            int version,
            String productCode,
            String modelName,
            int widthMm,
            int depthMm,
            int heightMm,
            int listPrice,
            DiscountType discountType,
            Integer discountValue,
            int salePrice,
            Integer stockQty,
            int reservedQty,
            ImageResponse mainImage,
            List<ImageResponse> images) {

        public static AdminOptionResponse of(ProductOption option, UnaryOperator<String> urlOf) {
            Dimensions dimensions = option.getDimensions();
            Price price = option.getPrice();
            List<ImageResponse> imageResponses = option.getImages().stream()
                .map(image -> ImageResponse.of(image.getImageKey(), urlOf))
                .toList();

            return new AdminOptionResponse(
                option.getId(),
                option.getVersion(),
                option.getProductCode(),
                option.getModelName(),
                dimensions.widthMm(),
                dimensions.depthMm(),
                dimensions.heightMm(),
                price.getListPrice(),
                price.getDiscountType(),
                price.getDiscountValue(),
                price.getSalePrice(),
                option.getStockQty(),
                option.getReservedQty(),
                ImageResponse.of(option.getMainImageKey(), urlOf),
                imageResponses);
        }
    }

    // ── 저장소 → 서비스 (조회 결과) ──

    public record CategoryProductCount(Long categoryId, long productCount) {
    }
}
