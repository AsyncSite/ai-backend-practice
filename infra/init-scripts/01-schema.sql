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


-- 시드 데이터는 02-seed-data.sql에서 관리합니다.
