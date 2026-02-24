-- =============================================================================
-- [Session 02 - Level 3] 인덱스 설계 실습
-- =============================================================================
-- 실제 서비스에서 발생하는 쿼리를 분석하고, 적절한 인덱스를 설계합니다.
--
-- 학습 목표:
--   1. EXPLAIN으로 쿼리 실행 계획을 분석할 수 있다
--   2. 쿼리 패턴에 맞는 인덱스를 설계할 수 있다
--   3. 복합 인덱스의 컬럼 순서를 이해한다
--   4. 불필요한 인덱스를 식별할 수 있다
--
-- 진행 방법:
--   1. 각 섹션의 쿼리를 EXPLAIN으로 실행해보세요
--   2. type, key, rows, Extra 컬럼을 확인하세요
--   3. TODO의 인덱스를 생성한 후 다시 EXPLAIN으로 비교하세요
-- =============================================================================

USE backend_study;

-- =============================================================================
-- 섹션 1: 현재 테이블 구조 확인
-- =============================================================================

-- 기존 인덱스 확인 (실습 전에 반드시 실행)
SHOW INDEX FROM orders;
SHOW INDEX FROM menus;
SHOW INDEX FROM restaurants;
SHOW INDEX FROM payments;

-- =============================================================================
-- 섹션 2: 주문 조회 쿼리 분석
-- =============================================================================

-- [쿼리 2-1] 특정 사용자의 최근 주문 조회
-- 실무에서 가장 빈번한 쿼리: "내 주문 내역"
EXPLAIN
SELECT * FROM orders
WHERE user_id = 1
ORDER BY created_at DESC
LIMIT 10;

-- Q: 위 EXPLAIN 결과에서 type은 무엇인가요?
--    key에 어떤 인덱스가 사용되었나요?
--    이미 idx_orders_user_created 인덱스가 있으므로 효율적입니다.


-- [쿼리 2-2] 특정 기간의 특정 상태 주문 조회
-- 관리자 화면: "오늘 접수된 주문 중 미처리 건"
EXPLAIN
SELECT * FROM orders
WHERE status = 'PENDING'
  AND created_at >= '2024-01-01'
  AND created_at < '2024-01-02'
ORDER BY created_at ASC;

-- TODO 1: 위 쿼리를 위한 복합 인덱스를 생성하세요
-- 힌트: WHERE 절의 등호(=) 조건 컬럼을 먼저, 범위 조건 컬럼을 나중에 배치합니다
-- 힌트: (status, created_at) 순서가 (created_at, status) 보다 효율적인 이유를 생각해보세요
-- CREATE INDEX idx_orders_status_created ON orders(???, ???);


-- [쿼리 2-3] 가게별 매출 집계
-- 가게 사장님 대시보드: "이번 달 매출"
EXPLAIN
SELECT restaurant_id, SUM(total_amount) as total_sales, COUNT(*) as order_count
FROM orders
WHERE restaurant_id = 5
  AND status IN ('PAID', 'COMPLETED')
  AND created_at >= '2024-01-01'
GROUP BY restaurant_id;

-- TODO 2: 위 쿼리를 위한 복합 인덱스를 생성하세요
-- 힌트: GROUP BY + WHERE 조건을 함께 고려하세요
-- 힌트: 커버링 인덱스를 만들면 테이블 접근 없이 인덱스만으로 처리 가능합니다
-- CREATE INDEX idx_orders_restaurant_status_created ON orders(???, ???, ???);


-- =============================================================================
-- 섹션 3: 메뉴 조회 쿼리 분석
-- =============================================================================

-- [쿼리 3-1] 가게의 판매 가능한 메뉴 목록 (가격순)
-- 고객 앱: "메뉴 보기"
EXPLAIN
SELECT * FROM menus
WHERE restaurant_id = 1
  AND is_available = TRUE
ORDER BY price ASC;

-- Q: idx_menus_restaurant_available 인덱스가 이미 있습니다.
--    ORDER BY price까지 인덱스로 처리하려면 어떻게 해야 할까요?

-- TODO 3: 정렬까지 커버하는 인덱스를 생성하세요
-- 힌트: 기존 인덱스를 확장하여 정렬 컬럼을 추가합니다
-- 주의: 기존 인덱스와 중복될 수 있으므로 기존 것을 삭제할지 고려하세요
-- CREATE INDEX idx_menus_restaurant_available_price ON menus(???, ???, ???);


-- [쿼리 3-2] 특정 가격 범위의 메뉴 검색
-- 고객 앱: "만원 이하 메뉴"
EXPLAIN
SELECT * FROM menus
WHERE restaurant_id = 1
  AND is_available = TRUE
  AND price <= 10000;

-- Q: TODO 3에서 생성한 인덱스가 이 쿼리에도 도움이 될까요? 왜 그런지 생각해보세요.


-- =============================================================================
-- 섹션 4: 결제 조회 쿼리 분석
-- =============================================================================

-- [쿼리 4-1] 멱등키로 결제 조회
-- 이중 결제 방지: "이미 처리된 결제인가?"
EXPLAIN
SELECT * FROM payments
WHERE idempotency_key = 'order-123-uuid-abc';

-- Q: 이 쿼리는 UNIQUE 인덱스를 사용합니다. type이 'const'인지 확인하세요.
--    const는 최대 1건만 반환하는 가장 빠른 조회입니다.


-- [쿼리 4-2] 기간별 결제 상태 조회
-- 정산 시스템: "어제 성공한 결제 목록"
EXPLAIN
SELECT * FROM payments
WHERE status = 'SUCCESS'
  AND created_at >= '2024-01-01'
  AND created_at < '2024-01-02';

-- TODO 4: 위 쿼리를 위한 인덱스를 생성하세요
-- CREATE INDEX idx_payments_status_created ON payments(???, ???);


-- =============================================================================
-- 섹션 5: 풀텍스트 검색 (심화)
-- =============================================================================

-- [쿼리 5-1] 메뉴 이름으로 검색
-- 고객 앱: "치킨" 검색
EXPLAIN
SELECT * FROM menus
WHERE name LIKE '%치킨%'
  AND is_available = TRUE;

-- Q: LIKE '%치킨%'은 인덱스를 사용할 수 없습니다. 왜 그럴까요?
-- Q: LIKE '치킨%'은 인덱스를 사용할 수 있을까요?

-- TODO 5: FULLTEXT 인덱스를 생성하여 검색 성능을 개선하세요
-- 힌트: MySQL에서 FULLTEXT 인덱스는 MATCH ... AGAINST 구문으로 사용합니다
-- ALTER TABLE menus ADD FULLTEXT INDEX ft_menus_name(???);

-- FULLTEXT 인덱스 사용 예시 (TODO 5 완료 후 실행):
-- SELECT * FROM menus
-- WHERE MATCH(name) AGAINST('치킨' IN BOOLEAN MODE)
--   AND is_available = TRUE;


-- =============================================================================
-- 섹션 6: 인덱스 효과 검증
-- =============================================================================

-- TODO 6: 위에서 생성한 인덱스들의 효과를 검증하세요
-- 각 쿼리를 다시 EXPLAIN으로 실행하고, 아래 항목을 비교하세요:
--
-- | 쿼리 | 인덱스 전 type | 인덱스 후 type | 인덱스 전 rows | 인덱스 후 rows |
-- |------|---------------|---------------|---------------|---------------|
-- | 2-2  |               |               |               |               |
-- | 2-3  |               |               |               |               |
-- | 3-1  |               |               |               |               |
-- | 4-2  |               |               |               |               |
--
-- type 개선: ALL -> ref -> range -> const 순으로 좋아집니다
-- rows 감소: 스캔하는 행 수가 줄어야 합니다


-- =============================================================================
-- 섹션 7: 불필요한 인덱스 식별 (심화)
-- =============================================================================

-- TODO 7: 현재 인덱스 중 중복되거나 불필요한 것이 있는지 분석하세요
--
-- 힌트: 아래 쿼리로 인덱스 사용 통계를 확인할 수 있습니다 (MySQL 8.0+)
-- SELECT
--     object_schema, object_name, index_name, count_star as usage_count
-- FROM performance_schema.table_io_waits_summary_by_index_usage
-- WHERE object_schema = 'backend_study'
-- ORDER BY count_star ASC;
--
-- Q: idx_menus_restaurant과 idx_menus_restaurant_available 중
--    하나를 삭제해도 될까요? 왜 그런지 설명해보세요.
