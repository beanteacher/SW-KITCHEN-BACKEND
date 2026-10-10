-- image_key 는 S3 객체 키(products/{UUID}.{확장자})만 저장한다. 파일 자체는 S3 에 있다
CREATE TABLE product_image (                     -- 제품 공통 추가 이미지
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT       NOT NULL,
  image_key   VARCHAR(100) NOT NULL,
  sort_order  INT          NOT NULL,
  CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE product_detail_image (              -- 상품설명 아래에 순서대로 이어 붙이는 상세 이미지
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT       NOT NULL,
  image_key   VARCHAR(100) NOT NULL,
  sort_order  INT          NOT NULL,
  CONSTRAINT fk_product_detail_image_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE option_image (                      -- 옵션 추가 이미지. 옵션 대표 이미지는 product_option.main_image_key
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_option_id  BIGINT       NOT NULL,
  image_key          VARCHAR(100) NOT NULL,
  sort_order         INT          NOT NULL,
  CONSTRAINT fk_option_image_option FOREIGN KEY (product_option_id) REFERENCES product_option (id)
);
