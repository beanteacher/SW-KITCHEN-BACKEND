CREATE TABLE product_spec (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT       NOT NULL,
  name        VARCHAR(50)  NOT NULL,             -- 예: 소비전력
  value       VARCHAR(200) NOT NULL,             -- 예: 1300W
  sort_order  INT          NOT NULL,
  CONSTRAINT uk_product_spec_name UNIQUE (product_id, name),
  CONSTRAINT fk_product_spec_product FOREIGN KEY (product_id) REFERENCES product (id)
);
