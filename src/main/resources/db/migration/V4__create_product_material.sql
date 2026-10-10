CREATE TABLE product_material (
  product_id  BIGINT      NOT NULL,
  material    VARCHAR(20) NOT NULL,
  PRIMARY KEY (product_id, material),
  CONSTRAINT ck_product_material CHECK (material IN ('STAINLESS', 'ALUMINUM', 'STEEL', 'PLASTIC')),
  CONSTRAINT fk_product_material_product FOREIGN KEY (product_id) REFERENCES product (id),
  INDEX ix_product_material (material)
);
