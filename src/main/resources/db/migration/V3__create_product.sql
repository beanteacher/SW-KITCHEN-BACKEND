CREATE TABLE product (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id        BIGINT       NOT NULL,
  name               VARCHAR(200) NOT NULL,
  sales_type         VARCHAR(10)  NOT NULL,
  manufacturer       VARCHAR(100) NOT NULL,
  origin             VARCHAR(50)  NOT NULL,
  description        TEXT         NULL,
  -- 처음 저장할 때 정하고 바꾸지 않는다. 분류 약어가 바뀌어도 이미 붙은 상품 코드는 그대로다
  code_prefix        CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  -- 지운 옵션 번호를 다시 쓰지 않으려고 마지막 번호를 남긴다
  last_option_no     TINYINT      NOT NULL DEFAULT 0,
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL,
  CONSTRAINT uk_product_code_prefix UNIQUE (code_prefix),
  CONSTRAINT ck_product_sales_type CHECK (sales_type IN ('NEW', 'STOCK', 'USED')),
  CONSTRAINT ck_product_last_option_no CHECK (last_option_no BETWEEN 0 AND 99),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
  INDEX ix_product_list (sales_type, category_id, created_at)
);
