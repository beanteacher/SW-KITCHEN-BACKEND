CREATE TABLE category (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id   BIGINT      NULL,
  name        VARCHAR(50) NOT NULL,
  abbr        CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sort_order  INT         NOT NULL,
  -- UNIQUE 는 NULL 끼리 겹침을 못 막으므로 대분류를 0 으로 묶어 약어 중복을 막는다
  parent_key  BIGINT AS (IFNULL(parent_id, 0)) STORED,
  CONSTRAINT uk_category_abbr UNIQUE (parent_key, abbr),
  CONSTRAINT ck_category_abbr CHECK (abbr REGEXP '^[A-Z]{2}$'),
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
);
