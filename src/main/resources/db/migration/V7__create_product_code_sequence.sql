-- 상품 코드 제품 번호를 접두어(대분류 약어 + 중분류 약어)별로 센다.
-- 중분류를 지우고 같은 약어로 다시 만들어도 번호가 이어져 옛 제품 코드와 겹치지 않는다
CREATE TABLE product_code_sequence (
  prefix   CHAR(4) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  last_no  INT NOT NULL,
  CONSTRAINT ck_product_code_sequence_no CHECK (last_no BETWEEN 0 AND 99999)
);
