SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- =============================================================================
-- 01-schema.sql
-- 음식 주문 서비스 스키마 정의 및 시드 데이터
-- =============================================================================
-- 이 스크립트는 MySQL 컨테이너 최초 기동 시 자동 실행됩니다.
-- (docker-entrypoint-initdb.d 마운트)
--
-- 테이블 구성:
--   users        - 회원 (고객, 사장, 관리자)
--   restaurants  - 가게
--   menus        - 메뉴 (가게별)
--   orders       - 주문
--   order_items  - 주문 항목
--   payments     - 결제
-- =============================================================================

USE backend_study;

-- -------------------------------------------
-- 회원 테이블 (세션 07: 인증/인가)
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(100)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    name        VARCHAR(50)   NOT NULL,
    phone       VARCHAR(20),
    role        VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 이메일 검색 최적화
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- 가게 테이블 (세션 01: 캐시, 세션 10: 아키텍처)
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS restaurants (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    category         VARCHAR(50),
    address          VARCHAR(255),
    phone            VARCHAR(20),
    min_order_amount INT          NOT NULL DEFAULT 0,
    delivery_fee     INT          NOT NULL DEFAULT 0,
    owner_id         BIGINT,
    is_open          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 사장님별 가게 조회, 카테고리별 가게 조회
    INDEX idx_restaurants_owner (owner_id),
    INDEX idx_restaurants_category (category),
    FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- 메뉴 테이블 (세션 01: 캐시, 세션 05: 동시성)
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS menus (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id  BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    price          INT          NOT NULL,
    stock          INT          NOT NULL DEFAULT 100,
    is_available   BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 낙관적 락용 버전 컬럼 (세션 05)
    version        BIGINT       NOT NULL DEFAULT 0,

    -- 가게별 메뉴 조회, 가게별 판매 가능 메뉴 조회
    INDEX idx_menus_restaurant (restaurant_id),
    INDEX idx_menus_restaurant_available (restaurant_id, is_available),
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- 주문 테이블 (세션 03, 04, 05)
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    restaurant_id   BIGINT       NOT NULL,
    total_amount    INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- 멱등성 키 (세션 03: 외부 연동 시 중복 방지)
    idempotency_key VARCHAR(50)  UNIQUE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 사용자별 주문 조회, 가게별 주문 조회
    INDEX idx_orders_user (user_id),
    INDEX idx_orders_restaurant (restaurant_id),
    -- 사용자별 최근 주문 조회 (커버링 인덱스)
    INDEX idx_orders_user_created (user_id, created_at),
    -- 상태별 주문 필터링
    INDEX idx_orders_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- 주문 항목 테이블
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id  BIGINT       NOT NULL,
    menu_id   BIGINT       NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    price     INT          NOT NULL,
    quantity  INT          NOT NULL,

    INDEX idx_order_items_order (order_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (menu_id)  REFERENCES menus(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------
-- 결제 테이블 (세션 03: 외부연동)
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT       NOT NULL UNIQUE,
    amount            INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    pg_transaction_id VARCHAR(100),
    idempotency_key   VARCHAR(50)  UNIQUE,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_payments_order (order_id),
    INDEX idx_payments_idempotency (idempotency_key),
    FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
-- 시드 데이터
-- =============================================================================
-- BCrypt 해싱된 비밀번호: 모두 'password123'
-- =============================================================================

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
(3, 5, '치즈볼',         5000, 2),  -- 5000 x 2 = 10000 (부족분은 배달비 포함)
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
