SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- =============================================================================
-- 02-seed-data.sql
-- 기본 참조 데이터 (회원, 가게, 메뉴)
-- =============================================================================
-- 이 스크립트는 03-seed-orders.sql이 의존하는 기본 데이터를 생성합니다.
-- BCrypt 해싱된 비밀번호: 모두 'password123'
-- =============================================================================

USE backend_study;

-- --- 회원 데이터 (3 고객 + 2 사장 + 1 관리자) ---
INSERT INTO users (email, password, name, phone, role) VALUES
('customer1@test.com', '$2b$10$C1XvXOTrg6Hlco1khtj5keCtxrWXIsZ8IaYlcEWhBzZPG5dwgUply', '김고객', '010-1111-1111', 'CUSTOMER'),
('customer2@test.com', '$2b$10$C1XvXOTrg6Hlco1khtj5keCtxrWXIsZ8IaYlcEWhBzZPG5dwgUply', '이고객', '010-2222-2222', 'CUSTOMER'),
('customer3@test.com', '$2b$10$C1XvXOTrg6Hlco1khtj5keCtxrWXIsZ8IaYlcEWhBzZPG5dwgUply', '박고객', '010-3333-3333', 'CUSTOMER'),
('owner1@test.com',    '$2b$10$C1XvXOTrg6Hlco1khtj5keCtxrWXIsZ8IaYlcEWhBzZPG5dwgUply', '최사장', '010-4444-4444', 'OWNER'),
('owner2@test.com',    '$2b$10$C1XvXOTrg6Hlco1khtj5keCtxrWXIsZ8IaYlcEWhBzZPG5dwgUply', '정사장', '010-5555-5555', 'OWNER'),
('admin@test.com',     '$2b$10$C1XvXOTrg6Hlco1khtj5keCtxrWXIsZ8IaYlcEWhBzZPG5dwgUply', '관리자', '010-9999-9999', 'ADMIN');

-- --- 가게 데이터 (3곳) ---
INSERT INTO restaurants (name, category, address, phone, min_order_amount, delivery_fee, owner_id) VALUES
('맛있는 치킨집',   '치킨',   '서울시 강남구 역삼동 123',  '02-1111-1111', 15000, 3000, 4),
('행복한 피자',     '피자',   '서울시 서초구 서초동 456',  '02-2222-2222', 18000, 2000, 4),
('건강한 샐러드',   '샐러드', '서울시 마포구 합정동 789',  '02-3333-3333', 12000, 2500, 5);

-- --- 메뉴 데이터 (13개) ---
-- 맛있는 치킨집 (restaurant_id = 1)
INSERT INTO menus (restaurant_id, name, description, price, stock) VALUES
(1, '후라이드 치킨',   '바삭바삭한 클래식 후라이드',           18000, 100),
(1, '양념 치킨',       '달콤 매콤한 양념 치킨',               19000, 100),
(1, '간장 치킨',       '간장 소스의 깊은 맛',                 20000, 100),
(1, '치킨 텐더',       '아이들이 좋아하는 순살 텐더',         12000, 50),
(1, '치즈볼',          '모짜렐라 치즈가 쭉쭉',                5000,  200);

-- 행복한 피자 (restaurant_id = 2)
INSERT INTO menus (restaurant_id, name, description, price, stock) VALUES
(2, '페퍼로니 피자',   '페퍼로니가 가득한 클래식 피자',       22000, 80),
(2, '마르게리타 피자', '신선한 토마토와 모짜렐라',            20000, 80),
(2, '콤비네이션 피자', '다양한 토핑의 조합',                  25000, 60),
(2, '갈릭 브레드',     '갈릭 버터가 풍부한 빵',               6000, 150);

-- 건강한 샐러드 (restaurant_id = 3)
INSERT INTO menus (restaurant_id, name, description, price, stock) VALUES
(3, '시저 샐러드',     '로메인 상추와 시저 드레싱',           13000, 100),
(3, '연어 샐러드',     '훈제 연어와 신선한 채소',             16000, 50),
(3, '닭가슴살 샐러드', '단백질 가득 다이어트 메뉴',           14000, 80),
(3, '과일 스무디',     '제철 과일로 만든 스무디',              7000, 200);

-- --- 주문 샘플 데이터 ---
INSERT INTO orders (user_id, restaurant_id, total_amount, status, created_at) VALUES
(1, 1, 37000, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(1, 2, 47000, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(2, 1, 24000, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 3, 29000, 'PAID',      DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, 2, 25000, 'PENDING',   NOW()),
(3, 1, 18000, 'PREPARING', NOW());

-- --- 주문 항목 샘플 ---
INSERT INTO order_items (order_id, menu_id, menu_name, price, quantity) VALUES
(1, 1, '후라이드 치킨', 18000, 1),
(1, 2, '양념 치킨',     19000, 1),
(2, 6, '페퍼로니 피자', 22000, 1),
(2, 9, '갈릭 브레드',    6000, 1),
(3, 1, '후라이드 치킨', 18000, 1),
(3, 5, '치즈볼',         5000, 2),
(4, 10, '시저 샐러드',  13000, 1),
(4, 11, '연어 샐러드',  16000, 1),
(5, 8, '콤비네이션 피자', 25000, 1),
(6, 1, '후라이드 치킨', 18000, 1);

-- --- 결제 샘플 ---
INSERT INTO payments (order_id, amount, status, pg_transaction_id, created_at) VALUES
(1, 37000, 'SUCCESS', 'PG-A1B2C3D4E5F6', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(2, 47000, 'SUCCESS', 'PG-B2C3D4E5F6G7', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(3, 24000, 'SUCCESS', 'PG-C3D4E5F6G7H8', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(4, 29000, 'SUCCESS', 'PG-D4E5F6G7H8I9', DATE_SUB(NOW(), INTERVAL 1 DAY));
