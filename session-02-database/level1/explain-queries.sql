-- [Session 02 - Level 1] EXPLAIN으로 쿼리 성능 분석하기
--
-- 이 파일은 인덱스 유무에 따른 쿼리 실행 계획을 비교합니다.
-- MySQL의 EXPLAIN 명령어로 쿼리가 어떻게 실행되는지 확인할 수 있습니다.

-- ============================================
-- 사전 준비: MySQL 접속
-- ============================================
-- docker exec -it grit-mysql mysql -uroot -proot1234 backend_study


-- ============================================
-- 1. 현재 인덱스 확인
-- ============================================
SHOW INDEX FROM orders;
-- 관찰: 어떤 컬럼에 인덱스가 있는지 확인


-- ============================================
-- 2. 인덱스 없이 쿼리 실행 (실습용으로 삭제)
-- ============================================
ALTER TABLE orders DROP INDEX IF EXISTS idx_orders_status;

-- EXPLAIN으로 실행 계획 확인
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';

-- 💡 관찰 포인트:
--   - type: ALL (풀 테이블 스캔 - 모든 행을 순회)
--   - rows: ~100000 (검사할 행 수)
--   - Extra: Using where


-- ============================================
-- 3. 인덱스 추가 후 쿼리 실행
-- ============================================
CREATE INDEX idx_orders_status ON orders(status);

-- 동일한 쿼리를 EXPLAIN으로 확인
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';

-- 💡 관찰 포인트:
--   - type: ref (인덱스를 사용한 참조)
--   - rows: ~20000 (훨씬 적은 행만 검사)
--   - possible_keys: idx_orders_status
--   - key: idx_orders_status (실제 사용된 인덱스)


-- ============================================
-- 4. 실행 시간 비교
-- ============================================

-- 4-1. 인덱스 없이 실행
ALTER TABLE orders DROP INDEX idx_orders_status;

SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';
-- Query time을 확인하세요 (MySQL 클라이언트 하단에 표시)

-- 4-2. 인덱스 있을 때 실행
CREATE INDEX idx_orders_status ON orders(status);

SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';
-- Query time을 다시 확인하고 비교하세요


-- ============================================
-- 5. 다양한 쿼리 패턴 실험
-- ============================================

-- 5-1. WHERE 조건이 여러 개일 때
EXPLAIN SELECT * FROM orders
WHERE status = 'COMPLETED' AND user_id = 1;
-- 어떤 인덱스가 사용되나요?

-- 5-2. ORDER BY와 함께 사용
EXPLAIN SELECT * FROM orders
WHERE status = 'COMPLETED'
ORDER BY created_at DESC
LIMIT 10;
-- Extra 필드에 'Using filesort'가 나타나면 정렬에 인덱스를 사용하지 못한 것


-- 5-3. COUNT 쿼리
EXPLAIN SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';
-- 인덱스만으로 카운트가 가능한지 확인 (Extra: Using index)


-- ============================================
-- 6. EXPLAIN의 type 필드 이해하기
-- ============================================

-- type: const (PRIMARY KEY나 UNIQUE 인덱스로 조회 - 최고 성능)
EXPLAIN SELECT * FROM orders WHERE id = 1;

-- type: ref (일반 인덱스로 조회 - 좋은 성능)
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';

-- type: range (범위 조회)
EXPLAIN SELECT * FROM orders WHERE created_at > '2024-01-01';

-- type: ALL (풀 테이블 스캔 - 나쁜 성능)
ALTER TABLE orders DROP INDEX idx_orders_status;
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';


-- ============================================
-- 정리: 인덱스 복원
-- ============================================
CREATE INDEX idx_orders_status ON orders(status);


-- ============================================
-- 💡 핵심 정리
-- ============================================
-- | type    | 설명                        | 성능 |
-- |---------|----------------------------|------|
-- | const   | PRIMARY KEY/UNIQUE 조회    | 최고 |
-- | ref     | 일반 인덱스 조회           | 좋음 |
-- | range   | 범위 스캔 (>, <, BETWEEN)  | 보통 |
-- | index   | 인덱스 풀 스캔             | 나쁨 |
-- | ALL     | 테이블 풀 스캔             | 최악 |
--
-- 🎯 목표: type이 ALL이 아닌 쿼리를 작성하는 것!
