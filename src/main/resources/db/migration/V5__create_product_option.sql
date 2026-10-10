CREATE TABLE product_option (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id      BIGINT       NOT NULL,
  option_no       TINYINT      NOT NULL,
  product_code    CHAR(13) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,   -- RFUR-00123-01
  model_name      VARCHAR(100) NULL,             -- 제조사 모델명, 고객에게 보이지 않는다
  width_mm        INT          NOT NULL,
  depth_mm        INT          NOT NULL,
  height_mm       INT          NOT NULL,
  list_price      INT          NOT NULL,
  discount_type   VARCHAR(10)  NOT NULL DEFAULT 'NONE',
  discount_value  INT          NULL,
  -- 가격순 정렬을 SQL 로 하려고 저장한다. 계산은 Java 에서만 한다
  sale_price      INT          NOT NULL,
  stock_qty       INT          NULL,             -- 신제품은 재고를 세지 않아 NULL
  reserved_qty    INT          NOT NULL DEFAULT 0,
  version         INT          NOT NULL DEFAULT 0,
  main_image_key  VARCHAR(100) NOT NULL,
  created_at      DATETIME(6)  NOT NULL,
  updated_at      DATETIME(6)  NOT NULL,
  CONSTRAINT uk_product_option_code UNIQUE (product_code),
  CONSTRAINT uk_product_option_no UNIQUE (product_id, option_no),
  CONSTRAINT uk_product_option_size UNIQUE (product_id, width_mm, depth_mm, height_mm),
  CONSTRAINT ck_product_option_no CHECK (option_no BETWEEN 1 AND 99),
  CONSTRAINT ck_product_option_size CHECK (width_mm >= 1 AND depth_mm >= 1 AND height_mm >= 1),
  CONSTRAINT ck_product_option_price CHECK (list_price >= 1 AND sale_price >= 0),
  CONSTRAINT ck_product_option_discount CHECK (
       (discount_type = 'NONE'   AND discount_value IS NULL)
    OR (discount_type = 'RATE'   AND discount_value BETWEEN 1 AND 99)
    OR (discount_type = 'AMOUNT' AND discount_value >= 1 AND discount_value < list_price)),
  CONSTRAINT ck_product_option_qty CHECK (reserved_qty >= 0 AND (stock_qty IS NULL AND reserved_qty = 0 OR stock_qty >= reserved_qty)),
  CONSTRAINT fk_product_option_product FOREIGN KEY (product_id) REFERENCES product (id)
);
