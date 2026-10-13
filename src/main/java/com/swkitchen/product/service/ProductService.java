package com.swkitchen.product.service;

import com.swkitchen.category.domain.Category;
import com.swkitchen.category.repository.CategoryRepository;
import com.swkitchen.common.config.ImageProperties;
import com.swkitchen.common.dto.ErrorResponse;
import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import com.swkitchen.product.domain.Dimensions;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    // 상품 코드 끝 두 자리
    private static final int MAX_OPTION_NO = 99;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCodeSequenceRepository productCodeSequenceRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductSpecRepository productSpecRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductDetailImageRepository productDetailImageRepository;
    private final OptionImageRepository optionImageRepository;
    private final ImageProperties imageProperties;

    /** 관리 화면(등록·수정)에 띄울 제품 전체 데이터를 읽는다 */
    @Transactional(readOnly = true)
    public ProductDto.AdminResponse getAdmin(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return toAdminResponse(product);
    }

    /** 제품을 새로 등록한다. 상품 코드 앞자리(예: RFUR-00001)를 발급하고 옵션마다 -01, -02 ... 를 붙인다 */
    public ProductDto.AdminResponse create(ProductDto.SaveRequest request) {
        // 1. 검사: 중분류인지, 옵션 치수·사양 이름이 겹치지 않는지
        Category category = findMiddleCategory(request.categoryId());
        checkDuplicates(request);

        // 2. 제품 본체와 재질 저장. 이때 분류별 다음 번호로 상품 코드 앞자리를 받는다
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

        // 3. 옵션(과 옵션 이미지), 사양, 제품 이미지를 차례로 저장
        for (ProductDto.OptionRequest req : request.options()) {
            createOption(product, request.salesType(), req);
        }
        saveSpecsAndImages(product, request);

        return toAdminResponse(product);
    }

    /**
     * 전체 수정. id 가 있는 옵션은 고치고, 없는 옵션은 새 번호로 만들고, 배열에서 빠진 옵션은 지운다.
     * 사양·이미지는 통째로 바꾼다
     */
    public ProductDto.AdminResponse update(Long id, ProductDto.SaveRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        Category category = findMiddleCategory(request.categoryId());
        checkDuplicates(request);

        List<ProductOption> existing = productOptionRepository.findByProductIdOrderByOptionNo(id);
        Map<Long, ProductOption> kept = checkKeptOptions(existing, request.options());
        List<ProductOption> removed = checkRemovableOptions(existing, kept.keySet());
        checkSalesTypeChange(product.getSalesType(), request.salesType(), existing);
        checkOptionLimit(product, request.options());

        // ── 여기부터 저장. 위 검사를 모두 통과해야 DB 를 건드린다 ──

        // 상품 코드는 분류가 바뀌어도 그대로다
        product.change(
            category,
            request.name(),
            request.salesType(),
            request.manufacturer(),
            request.origin(),
            request.description());
        product.changeMaterials(request.materials() == null ? List.of() : request.materials());
        deleteReplacedRows(id, existing, removed);
        changeKeptOptions(kept, request);
        createNewOptions(product, request);
        saveSpecsAndImages(product, request);

        return toAdminResponse(product);
    }

    /**
     * 기존 옵션을 가리키는 요청(id 있음)을 하나씩 검사하고, 남길 옵션을 id 로 찾을 수 있게 돌려준다
     * - 이 제품의 옵션인지 (같은 id 를 두 번 보내도 거부)
     * - 화면을 연 뒤 다른 곳에서 고치지 않았는지 (version 비교)
     * - 재고를 잡아 둔 수량보다 적게 줄이지 않는지
     */
    private static Map<Long, ProductOption> checkKeptOptions(
            List<ProductOption> existing,
            List<ProductDto.OptionRequest> requests) {
        Map<Long, ProductOption> existingById = existing.stream()
            .collect(Collectors.toMap(ProductOption::getId, Function.identity()));
        Map<Long, ProductOption> kept = new HashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            ProductDto.OptionRequest req = requests.get(i);
            if (req.id() == null) {
                continue;
            }
            ProductOption option = existingById.get(req.id());
            if (option == null || kept.put(req.id(), option) != null) {
                throw fieldError(ErrorCode.OPTION_NOT_IN_PRODUCT, "options[%d].id".formatted(i));
            }
            if (option.getVersion() != req.version()) {
                throw fieldError(ErrorCode.CONCURRENT_MODIFICATION, "options[%d].version".formatted(i));
            }
            if (req.stockQty() != null && req.stockQty() < option.getReservedQty()) {
                throw fieldError(ErrorCode.STOCK_BELOW_RESERVED, "options[%d].stockQty".formatted(i));
            }
        }
        return kept;
    }

    /** 요청에 없는 기존 옵션 = 지울 옵션. 잡아 둔 수량이 있으면 지울 수 없다 */
    private static List<ProductOption> checkRemovableOptions(List<ProductOption> existing, Set<Long> keptIds) {
        List<ProductOption> removed = existing.stream()
            .filter(option -> !keptIds.contains(option.getId()))
            .toList();
        // 주문·견적 기능을 만들면 거기에 쓰인 옵션도 지우지 못하게 막는다
        if (removed.stream().anyMatch(option -> option.getReservedQty() > 0)) {
            throw new AppException(ErrorCode.OPTION_IN_USE);
        }
        return removed;
    }

    /** 새 옵션 번호가 99번을 넘지 않는지 본다. 지운 번호는 다시 쓰지 않아 지운 것도 센다 */
    private static void checkOptionLimit(Product product, List<ProductDto.OptionRequest> requests) {
        long newCount = requests.stream().filter(req -> req.id() == null).count();
        if (product.getLastOptionNo() + newCount > MAX_OPTION_NO) {
            throw new AppException(ErrorCode.OPTION_LIMIT_EXCEEDED);
        }
    }

    /**
     * 옵션 이미지·사양·제품 이미지는 통째로 지우고 뒤에서 다시 넣는다. 빠진 옵션도 여기서 지운다.
     * 새로 넣는 행과 지울 행이 같은 치수·이름일 수 있어 지우기는 바로 실행한다
     */
    private void deleteReplacedRows(Long productId, List<ProductOption> existing, List<ProductOption> removed) {
        List<Long> existingIds = existing.stream().map(ProductOption::getId).toList();
        optionImageRepository.deleteAllInBatch(optionImageRepository.findByProductOptionIdInOrderBySortOrder(existingIds));
        productOptionRepository.deleteAllInBatch(removed);
        productSpecRepository.deleteAllInBatch(productSpecRepository.findByProductIdOrderBySortOrder(productId));
        productImageRepository.deleteAllInBatch(productImageRepository.findByProductIdOrderBySortOrder(productId));
        productDetailImageRepository.deleteAllInBatch(productDetailImageRepository.findByProductIdOrderBySortOrder(productId));
    }

    /** 남은 기존 옵션을 고치고 그 이미지를 다시 넣는다 */
    private void changeKeptOptions(Map<Long, ProductOption> kept, ProductDto.SaveRequest request) {
        for (ProductDto.OptionRequest req : request.options()) {
            if (req.id() == null) {
                continue;
            }
            ProductOption option = kept.get(req.id());
            option.change(
                req.modelName(),
                req.toDimensions(),
                req.toPrice(),
                stockQty(request.salesType(), req),
                req.mainImageKey());
            saveOptionImages(option, req.imageKeys());
        }
        // 고친 옵션의 원래 치수로 새 옵션을 넣을 수 있게 고친 내용을 먼저 반영한다
        // ponytail: 기존 옵션 둘의 치수를 맞바꾸면 반영 도중 치수 겹침으로 500 이 난다. 필요해지면 임시 치수를 거쳐 바꾼다
        productOptionRepository.flush();
    }

    /** 새 옵션(id 없음)을 다음 번호로 만든다 */
    private void createNewOptions(Product product, ProductDto.SaveRequest request) {
        for (ProductDto.OptionRequest req : request.options()) {
            if (req.id() == null) {
                createOption(product, request.salesType(), req);
            }
        }
    }

    /** 저장된 사양·이미지·옵션을 다시 읽어 화면용 응답으로 넘긴다. 응답 모양은 DTO 가 만든다 */
    private ProductDto.AdminResponse toAdminResponse(Product product) {
        Long productId = product.getId();
        List<ProductSpec> specs = productSpecRepository.findByProductIdOrderBySortOrder(productId);
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(productId);
        List<ProductDetailImage> detailImages = productDetailImageRepository.findByProductIdOrderBySortOrder(productId);
        List<ProductOption> options = productOptionRepository.findByProductIdOrderByOptionNo(productId);

        // 옵션 이미지는 옵션마다 따로 읽지 않고 한 번에 읽어 옵션 id 별로 나눈다
        List<Long> optionIds = options.stream().map(ProductOption::getId).toList();
        Map<Long, List<OptionImage>> optionImages = optionImageRepository
            .findByProductOptionIdInOrderBySortOrder(optionIds).stream()
            .collect(Collectors.groupingBy(image -> image.getProductOption().getId()));

        return ProductDto.AdminResponse.of(
            product,
            specs,
            images,
            detailImages,
            options,
            optionImages,
            imageProperties::urlOf);
    }

    /** 옵션 하나를 다음 번호로 만들고 그 이미지까지 저장한다 */
    private void createOption(Product product, SalesType salesType, ProductDto.OptionRequest req) {
        ProductOption option = productOptionRepository.save(ProductOption.create(
            product,
            req.modelName(),
            req.toDimensions(),
            req.toPrice(),
            stockQty(salesType, req),
            req.mainImageKey()));
        saveOptionImages(option, req.imageKeys());
    }

    private void saveOptionImages(ProductOption option, List<String> imageKeys) {
        optionImageRepository.saveAll(ordered(imageKeys, (key, i) -> OptionImage.create(option, key, i)));
    }

    /** 사양·제품 이미지·상세 이미지를 배열 순서대로 저장한다 */
    private void saveSpecsAndImages(Product product, ProductDto.SaveRequest request) {
        productSpecRepository.saveAll(ordered(request.specs(),
            (spec, i) -> ProductSpec.create(product, spec.name(), spec.value(), i)));
        productImageRepository.saveAll(ordered(request.imageKeys(), (key, i) -> ProductImage.create(product, key, i)));
        productDetailImageRepository.saveAll(ordered(request.detailImageKeys(),
            (key, i) -> ProductDetailImage.create(product, key, i)));
    }

    /** 신제품은 재고를 세지 않는다. 보내도 버린다 */
    private static Integer stockQty(SalesType salesType, ProductDto.OptionRequest req) {
        return salesType == SalesType.NEW ? null : req.stockQty();
    }

    /** 뒤에 나온 쪽에 오류를 붙인다. 사양 이름은 앞뒤 공백을 빼고 비교한다 */
    private static void checkDuplicates(ProductDto.SaveRequest request) {
        Set<Dimensions> sizes = new HashSet<>();
        for (int i = 0; i < request.options().size(); i++) {
            if (!sizes.add(request.options().get(i).toDimensions())) {
                throw fieldError(ErrorCode.OPTION_SIZE_DUPLICATE, "options[%d].widthMm".formatted(i));
            }
        }
        List<ProductDto.SpecRequest> specs = request.specs() == null ? List.of() : request.specs();
        Set<String> specNames = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            if (!specNames.add(specs.get(i).name().strip())) {
                throw fieldError(ErrorCode.SPEC_NAME_DUPLICATE, "specs[%d].name".formatted(i));
            }
        }
    }

    /** 신제품↔재고품만 오간다. 재고품을 신제품으로 바꿀 때는 잡아 둔 수량이 없어야 한다. 중고품은 오가지 않는다 */
    private static void checkSalesTypeChange(SalesType from, SalesType to, List<ProductOption> options) {
        if (from == to) {
            return;
        }
        if (from == SalesType.NEW && to == SalesType.STOCK) {
            return;
        }
        if (from == SalesType.STOCK && to == SalesType.NEW
            && options.stream().allMatch(option -> option.getReservedQty() == 0)) {
            return;
        }
        throw fieldError(ErrorCode.SALES_TYPE_CHANGE_NOT_ALLOWED, "salesType");
    }

    /** 화면이 어느 칸에 오류를 띄울지 알 수 있게 칸 경로를 붙인 예외를 만든다 */
    private static AppException fieldError(ErrorCode code, String field) {
        return new AppException(code, List.of(new ErrorResponse.FieldError(field, code.getMessage())));
    }

    /** 제품은 중분류에만 넣는다. 없는 분류·대분류면 거부한다 */
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
