SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- =============================================================================
-- 03-seed-orders.sql
-- 실습용 주문 데이터 (세션 02: EXPLAIN 분석용 대량 데이터)
-- =============================================================================
-- 프로시저를 사용하여 10만 건의 주문 데이터를 자동 생성합니다.
-- 세션 02 실습에서 인덱스 유무에 따른 성능 차이를 확인하기 위해 사용합니다.
-- =============================================================================

USE backend_study;

-- 대량 주문 데이터 생성 프로시저
DELIMITER //
CREATE PROCEDURE generate_orders()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE user_count INT DEFAULT 3;       -- customer1~3
    DECLARE restaurant_count INT DEFAULT 3;  -- 가게 3곳 (치킨, 피자, 샐러드)
    DECLARE random_user INT;
    DECLARE random_restaurant INT;
    DECLARE random_amount INT;
    DECLARE random_status VARCHAR(20);
    DECLARE statuses VARCHAR(100) DEFAULT 'PENDING,PAID,PREPARING,DELIVERING,COMPLETED';

    -- 10만 건 생성
    WHILE i < 100000 DO
        SET random_user = FLOOR(1 + RAND() * user_count);
        SET random_restaurant = FLOOR(1 + RAND() * restaurant_count);
        SET random_amount = FLOOR(10000 + RAND() * 50000);

        -- 랜덤 상태
        SET random_status = ELT(FLOOR(1 + RAND() * 5), 'PENDING', 'PAID', 'PREPARING', 'DELIVERING', 'COMPLETED');

        INSERT INTO orders (user_id, restaurant_id, total_amount, status, created_at)
        VALUES (
            random_user,
            random_restaurant,
            random_amount,
            random_status,
            -- 최근 1년 범위의 랜덤 날짜
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
        );

        SET i = i + 1;

        -- 1만 건마다 진행 상황 로그
        IF i % 10000 = 0 THEN
            SELECT CONCAT('Generated ', i, ' orders') AS progress;
        END IF;
    END WHILE;
END //
DELIMITER ;

-- 프로시저 실행
CALL generate_orders();

-- 프로시저 정리
DROP PROCEDURE IF EXISTS generate_orders;

-- -------------------------------------------
-- 세션 02 실습용: 인덱스 없는 상태 확인
-- (인덱스는 이미 테이블 생성 시 추가되었으므로,
--  실습에서 인덱스를 삭제했다가 다시 추가하는 방식으로 진행)
-- -------------------------------------------
-- 참고: 아래 명령어로 인덱스 삭제 가능 (세션 02 실습 시 사용)
-- ALTER TABLE orders DROP INDEX idx_orders_user_created;
-- ALTER TABLE orders DROP INDEX idx_orders_status;
