package com.swkitchen.product.service;

import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.config.ImageProperties;
import com.swkitchen.common.dto.ErrorResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.product.domain.OptionImage;
import com.swkitchen.product.domain.Product;
import com.swkitchen.product.domain.ProductCodeSequence;
import com.swkitchen.product.domain.ProductDetailImage;
import com.swkitchen.product.domain.ProductImage;
import com.swkitchen.product.domain.ProductOption;
import com.swkitchen.product.domain.ProductSpec;
import com.swkitchen.product.domain.SalesType;
import com.swkitchen.product.dto.ProductDto;
import com.swkitchen.product.repository.OptionImageRepository;
import com.swkitchen.product.repository.ProductCodeSequenceRepository;
import com.swkitchen.product.repository.ProductDetailImageRepository;
import com.swkitchen.product.repository.ProductImageRepository;
import com.swkitchen.product.repository.ProductOptionRepository;
import com.swkitchen.product.repository.ProductRepository;
import com.swkitchen.product.repository.ProductSpecRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품은 옵션·사양·재질·이미지까지 한 트랜잭션으로 저장한다. 하나라도 실패하면 발급하던 상품 코드 번호까지 전부 되돌린다.
 * 그래서 이 안에서 따로 트랜잭션을 열거나 예외를 삼키지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCodeSequenceRepository productCodeSequenceRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductSpecRepository productSpecRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductDetailImageRepository productDetailImageRepository;
    private final OptionImageRepository optionImageRepository;
    private final ImageProperties imageProperties;

    @Transactional(readOnly = true)
    public ProductDto.AdminResponse getAdmin(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return toAdminResponse(product);
    }

    public ProductDto.AdminResponse create(ProductDto.SaveRequest request) {
        Category category = findMiddleCategory(request.categoryId());

        Product product = Product.create(
            category,
            request.name(),
            request.salesType(),
            request.manufacturer(),
            request.origin(),
            request.description(),
            issueCodePrefix(category));
        if (request.materials() != null) {
            product.changeMaterials(request.materials());
        }
        productRepository.save(product);

        for (ProductDto.OptionRequest req : request.options()) {
            // 신제품은 재고를 세지 않는다. 보내도 버린다
            Integer stockQty = request.salesType() == SalesType.NEW ? null : req.stockQty();
            ProductOption option = productOptionRepository.save(ProductOption.create(
                product,
                req.modelName(),
                req.widthMm(),
                req.depthMm(),
                req.heightMm(),
                req.listPrice(),
                req.discountType(),
                req.discountValue(),
                stockQty,
                req.mainImageKey()));
            optionImageRepository.saveAll(ordered(req.imageKeys(), (key, i) -> OptionImage.create(option, key, i)));
        }
        productSpecRepository.saveAll(ordered(request.specs(),
            (spec, i) -> ProductSpec.create(product, spec.name(), spec.value(), i)));
        productImageRepository.saveAll(ordered(request.imageKeys(), (key, i) -> ProductImage.create(product, key, i)));
        productDetailImageRepository.saveAll(ordered(request.detailImageKeys(),
            (key, i) -> ProductDetailImage.create(product, key, i)));

        return toAdminResponse(product);
    }

    private ProductDto.AdminResponse toAdminResponse(Product product) {
        Long productId = product.getId();
        List<ProductDto.MaterialResponse> materials = product.getMaterials().stream()
            .sorted()
            .map(ProductDto.MaterialResponse::from)
            .toList();
        List<ProductDto.SpecResponse> specs = productSpecRepository
            .findByProductIdOrderBySortOrder(productId).stream()
            .map(ProductDto.SpecResponse::from)
            .toList();
        List<ProductDto.ImageResponse> images = productImageRepository
            .findByProductIdOrderBySortOrder(productId).stream()
            .map(image -> image(image.getImageKey()))
            .toList();
        List<ProductDto.ImageResponse> detailImages = productDetailImageRepository
            .findByProductIdOrderBySortOrder(productId).stream()
            .map(image -> image(image.getImageKey()))
            .toList();

        List<ProductOption> options = productOptionRepository.findByProductIdOrderByOptionNo(productId);
        List<Long> optionIds = options.stream().map(ProductOption::getId).toList();
        Map<Long, List<ProductDto.ImageResponse>> optionImages = optionImageRepository
            .findByProductOptionIdInOrderBySortOrder(optionIds).stream()
            .collect(Collectors.groupingBy(
                image -> image.getProductOption().getId(),
                Collectors.mapping(image -> image(image.getImageKey()), Collectors.toList())));
        List<ProductDto.AdminOptionResponse> optionResponses = options.stream()
            .map(option -> ProductDto.AdminOptionResponse.of(
                option,
                image(option.getMainImageKey()),
                optionImages.getOrDefault(option.getId(), List.of())))
            .toList();

        return new ProductDto.AdminResponse(
            productId,
            product.getName(),
            product.getSalesType(),
            ProductDto.CategoryRef.from(product.getCategory()),
            product.getManufacturer(),
            product.getOrigin(),
            materials,
            specs,
            product.getDescription(),
            images,
            detailImages,
            optionResponses);
    }

    private ProductDto.ImageResponse image(String key) {
        return new ProductDto.ImageResponse(key, imageProperties.urlOf(key));
    }

    private Category findMiddleCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND,
                List.of(new ErrorResponse.FieldError("categoryId", ErrorCode.CATEGORY_NOT_FOUND.getMessage()))));
        if (category.isTop()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_MIDDLE,
                List.of(new ErrorResponse.FieldError("categoryId", "제품은 중분류에만 넣을 수 있습니다.")));
        }
        return category;
    }

    /** 대분류 약어 + 중분류 약어(예: RFUR) 마다 번호를 이어 붙인다. 예: RFUR-00124 */
    private String issueCodePrefix(Category middle) {
        String prefix = middle.getParent().getAbbr() + middle.getAbbr();
        // ponytail: 처음 쓰는 접두어로 두 명이 동시에 등록하면 한쪽이 기본 키 겹침으로 500 이 난다. 자주 생기면 분류를 만들 때 행을 같이 만든다
        ProductCodeSequence sequence = productCodeSequenceRepository.findByPrefix(prefix)
            .orElseGet(() -> productCodeSequenceRepository.save(ProductCodeSequence.create(prefix)));
        return sequence.issue();
    }

    /** 배열 순서를 표시 순서(1부터)로 저장한다 */
    private static <T, E> List<E> ordered(List<T> items, BiFunction<T, Integer, E> create) {
        List<E> result = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                result.add(create.apply(items.get(i), i + 1));
            }
        }
        return result;
    }
}
