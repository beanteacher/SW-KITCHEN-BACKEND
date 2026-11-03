-- k6 성능 측정용 데이터. 측정용 DB(sw_kitchen_perf)에만 넣는다. 개발용 DB 에 넣으면 기존 제품이 지워진다
-- 대분류 4개 × 중분류 5개, 제품 3,000개(옵션 3개씩 9,000개). 다시 돌리면 지우고 처음부터 넣는다
-- 실행: docker compose exec -T mysql mysql -usw_kitchen -psw_kitchen sw_kitchen_perf < perf/seed.sql

-- 컨테이너의 mysql 클라이언트는 기본이 latin1 이라 한글이 깨져 들어간다
SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 10000;

DELETE FROM option_image;
DELETE FROM product_detail_image;
DELETE FROM product_image;
DELETE FROM product_spec;
DELETE FROM product_option;
DELETE FROM product_material;
DELETE FROM product;
DELETE FROM product_code_sequence;
DELETE FROM category WHERE parent_id IS NOT NULL;
DELETE FROM category;

INSERT INTO category (id, parent_id, name, abbr, sort_order) VALUES
  (1, NULL, '냉장·냉동', 'RF', 1),
  (2, NULL, '가열 기기', 'HT', 2),
  (3, NULL, '작업대·싱크', 'WT', 3),
  (4, NULL, '수납·선반', 'SH', 4);

-- 중분류 id 5~24. 대분류마다 5개, 약어 AA~AE
INSERT INTO category (id, parent_id, name, abbr, sort_order)
WITH RECURSIVE n AS (SELECT 0 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 19)
SELECT 5 + i, 1 + i DIV 5, CONCAT('중분류 ', 1 + i DIV 5, '-', 1 + i MOD 5), CONCAT('A', CHAR(65 + i MOD 5)), 1 + i MOD 5
FROM n;

-- 제품 id 1~3000. 중분류·판매 구분·이름 낱말을 고르게 섞는다
INSERT INTO product (id, category_id, name, sales_type, manufacturer, origin, description,
                     code_prefix, last_option_no, created_at, updated_at)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 3000)
SELECT n.i,
       c.id,
       CONCAT(ELT(1 + n.i MOD 8, '업소용 냉장고', '냉동고', '가스레인지', '튀김기', '작업대', '싱크대', '벽선반', '컨벡션 오븐'), ' ', n.i),
       ELT(1 + n.i MOD 3, 'NEW', 'STOCK', 'USED'),
       ELT(1 + n.i MOD 5, '한일주방', '대성기업', '그랜드우성', '스타리온', '라셀르'),
       '국산',
       '성능 측정용 제품입니다.',
       CONCAT(p.abbr, c.abbr, '-', LPAD(n.i, 5, '0')),
       3,
       NOW(6) - INTERVAL n.i MINUTE,
       NOW(6) - INTERVAL n.i MINUTE
FROM n
JOIN category c ON c.id = 5 + n.i MOD 20
JOIN category p ON p.id = c.parent_id;

-- 재질 1개, 세 개 중 하나는 2개
INSERT INTO product_material (product_id, material)
SELECT id, ELT(1 + id MOD 4, 'STAINLESS', 'ALUMINUM', 'STEEL', 'PLASTIC') FROM product
UNION ALL
SELECT id, ELT(1 + (id + 1) MOD 4, 'STAINLESS', 'ALUMINUM', 'STEEL', 'PLASTIC') FROM product WHERE id MOD 3 = 0;

INSERT INTO product_spec (product_id, name, value, sort_order)
SELECT id, '소비전력', CONCAT(500 + id MOD 10 * 100, 'W'), 1 FROM product
UNION ALL SELECT id, '전압', '220V', 2 FROM product
UNION ALL SELECT id, '무게', CONCAT(20 + id MOD 50, 'kg'), 3 FROM product;

-- 옵션 3개씩. 재고품·중고품은 다섯 중 하나꼴로 보이는 수량이 0 인 옵션이 생긴다
INSERT INTO product_option (product_id, option_no, product_code, model_name, width_mm, depth_mm, height_mm,
                            list_price, discount_type, discount_value, sale_price, stock_qty,
                            main_image_key, created_at, updated_at)
WITH RECURSIVE o AS (SELECT 1 AS no UNION ALL SELECT no + 1 FROM o WHERE no < 3)
SELECT p.id, o.no, CONCAT(p.code_prefix, '-', LPAD(o.no, 2, '0')), CONCAT('M-', p.id, '-', o.no),
       600 + o.no * 300, 700, 850,
       100000 * (1 + p.id MOD 20) + o.no * 50000, 'NONE', NULL, 100000 * (1 + p.id MOD 20) + o.no * 50000,
       IF(p.sales_type = 'NEW', NULL, (p.id + o.no) MOD 5),
       'products/perf.jpg', p.created_at, p.created_at
FROM product p CROSS JOIN o;

INSERT INTO product_image (product_id, image_key, sort_order)
SELECT id, 'products/perf-1.jpg', 1 FROM product
UNION ALL SELECT id, 'products/perf-2.jpg', 2 FROM product;

INSERT INTO product_detail_image (product_id, image_key, sort_order)
SELECT id, 'products/perf-detail-1.jpg', 1 FROM product
UNION ALL SELECT id, 'products/perf-detail-2.jpg', 2 FROM product;

INSERT INTO option_image (product_option_id, image_key, sort_order)
SELECT id, 'products/perf-option.jpg', 1 FROM product_option;

INSERT INTO product_code_sequence (prefix, last_no)
SELECT LEFT(code_prefix, 4), MAX(CAST(RIGHT(code_prefix, 5) AS UNSIGNED)) FROM product GROUP BY LEFT(code_prefix, 4);

ANALYZE TABLE category, product, product_material, product_option, product_spec;
