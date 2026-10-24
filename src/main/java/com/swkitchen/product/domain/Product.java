package com.swkitchen.product.domain;

import com.swkitchen.category.domain.Category;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 중분류만 가리킨다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SalesType salesType;

    @Column(nullable = false, length = 100)
    private String manufacturer;

    @Column(nullable = false, length = 50)
    private String origin;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, updatable = false, columnDefinition = "char(10)")
    private String codePrefix;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int lastOptionNo;

    @ElementCollection
    @CollectionTable(name = "product_material", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "material", nullable = false, length = 20)
    private Set<Material> materials = new HashSet<>();

    // 아래 목록은 읽기용이다. 저장·삭제는 각 Repository 로 하고, 자식이 만들어지고 지워질 때 이 목록도 함께 맞춘다
    @OneToMany(mappedBy = "product")
    @OrderBy("optionNo")
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @OrderBy("sortOrder")
    private List<ProductSpec> specs = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @OrderBy("sortOrder")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    @OrderBy("sortOrder")
    private List<ProductDetailImage> detailImages = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Product create(Category category, String name, SalesType salesType, String manufacturer, String origin,
            String description, String codePrefix) {
        Product product = new Product();
        product.category = category;
        product.name = name;
        product.salesType = salesType;
        product.manufacturer = manufacturer;
        product.origin = origin;
        product.description = description;
        product.codePrefix = codePrefix;
        return product;
    }

    /** 상품 코드 앞자리는 분류를 바꿔도 그대로 둔다 */
    public void change(Category category, String name, SalesType salesType, String manufacturer, String origin,
            String description) {
        this.category = category;
        this.name = name;
        this.salesType = salesType;
        this.manufacturer = manufacturer;
        this.origin = origin;
        this.description = description;
    }

    /** 지운 옵션의 번호는 다시 쓰지 않는다 */
    public int nextOptionNo() {
        return ++lastOptionNo;
    }

    public void changeMaterials(Collection<Material> materials) {
        this.materials.clear();
        this.materials.addAll(materials);
    }

    public Set<Material> getMaterials() {
        return Collections.unmodifiableSet(materials);
    }

    public List<ProductOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    /** 옵션을 지울 때 이 목록에서도 뺀다 */
    public void removeOptions(List<ProductOption> removed) {
        options.removeAll(removed);
    }

    public List<ProductSpec> getSpecs() {
        return Collections.unmodifiableList(specs);
    }

    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public List<ProductDetailImage> getDetailImages() {
        return Collections.unmodifiableList(detailImages);
    }

    /** 사양·제품 이미지·상세 이미지를 통째로 지울 때 이 목록들도 비운다 */
    public void clearSpecsAndImages() {
        specs.clear();
        images.clear();
        detailImages.clear();
    }

    // 아래는 각 자식의 create 만 부른다
    void addOption(ProductOption option) {
        options.add(option);
    }

    void addSpec(ProductSpec spec) {
        specs.add(spec);
    }

    void addImage(ProductImage image) {
        images.add(image);
    }

    void addDetailImage(ProductDetailImage image) {
        detailImages.add(image);
    }
}
