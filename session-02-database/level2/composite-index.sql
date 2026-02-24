-- [Session 02 - Level 2] 복합 인덱스 실험
--
-- 복합 인덱스의 컬럼 순서가 성능에 미치는 영향을 이해합니다.
-- 인덱스의 최좌측 컬럼 원칙(Leftmost Prefix Rule)을 직접 확인할 수 있습니다.

-- ============================================
-- 사전 준비
-- ============================================
-- docker exec -it grit-mysql mysql -uroot -proot1234 backend_study


-- ============================================
-- 1. 복합 인덱스: (user_id, status) 순서
-- ============================================

-- 기존 인덱스 제거
DROP INDEX IF EXISTS idx_orders_status ON orders;

-- 복합 인덱스 생성: user_id를 앞에
CREATE INDEX idx_user_status ON orders(user_id, status);

-- 1-1. 두 컬럼 모두 사용하는 쿼리
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'COMPLETED';
-- 💡 관찰: 인덱스가 잘 사용됨 (key: idx_user_status)

-- 1-2. 첫 번째 컬럼만 사용하는 쿼리
EXPLAIN SELECT * FROM orders WHERE user_id = 1;
-- 💡 관찰: 인덱스 사용 가능 (최좌측 컬럼 원칙)

-- 1-3. 두 번째 컬럼만 사용하는 쿼리
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';
-- 💡 관찰: 인덱스 사용 불가능! (type: ALL 또는 index)
-- 복합 인덱스는 첫 번째 컬럼 없이 두 번째 컬럼만으로는 사용할 수 없음


-- ============================================
-- 2. 복합 인덱스: (status, user_id) 순서 변경
-- ============================================

-- 기존 인덱스 제거
DROP INDEX idx_user_status ON orders;

-- 복합 인덱스 생성: status를 앞에
CREATE INDEX idx_status_user ON orders(status, user_id);

-- 2-1. 두 컬럼 모두 사용하는 쿼리
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'COMPLETED';
-- 💡 관찰: WHERE 절의 순서와 무관하게 인덱스 사용됨

-- 2-2. status만 사용하는 쿼리
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';
-- 💡 관찰: 이번엔 status만으로도 인덱스 사용 가능!

-- 2-3. user_id만 사용하는 쿼리
EXPLAIN SELECT * FROM orders WHERE user_id = 1;
-- 💡 관찰: 인덱스 사용 불가능


-- ============================================
-- 3. 카디널리티(Cardinality) 고려
-- ============================================

-- 각 컬럼의 고유값 개수 확인
SELECT
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT status) as unique_statuses,
    COUNT(*) as total_orders
FROM orders;

-- 💡 분석:
--   - user_id: 6개 (낮은 카디널리티)
--   - status: 4개 (PENDING, CONFIRMED, COMPLETED, CANCELLED)
--   - 이 경우 user_id가 더 선택적이므로 앞에 두는 것이 일반적으로 유리


-- ============================================
-- 4. 복합 인덱스 활용 패턴
-- ============================================

-- 복합 인덱스 재생성: (user_id, status, created_at)
DROP INDEX IF EXISTS idx_status_user ON orders;
CREATE INDEX idx_user_status_created ON orders(user_id, status, created_at);

-- 4-1. 모든 컬럼 사용 (최적)
EXPLAIN SELECT * FROM orders
WHERE user_id = 1 AND status = 'COMPLETED' AND created_at > '2024-01-01';
-- 인덱스 완전 활용

-- 4-2. 앞 두 컬럼만 사용
EXPLAIN SELECT * FROM orders
WHERE user_id = 1 AND status = 'COMPLETED';
-- 인덱스 일부 활용 (여전히 좋음)

-- 4-3. 첫 번째와 세 번째 컬럼만 사용
EXPLAIN SELECT * FROM orders
WHERE user_id = 1 AND created_at > '2024-01-01';
-- 💡 관찰: created_at은 인덱스를 완전히 활용하지 못함 (range로만 사용 가능)


-- ============================================
-- 5. 인덱스 오버헤드 확인
-- ============================================

-- 현재 인덱스 목록과 크기
SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    CARDINALITY
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'backend_study' AND TABLE_NAME = 'orders'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 💡 주의: 인덱스가 많으면 조회는 빠르지만 INSERT/UPDATE/DELETE가 느려짐


-- ============================================
-- 정리: 실습용 인덱스 제거
-- ============================================
DROP INDEX IF EXISTS idx_user_status_created ON orders;

-- 기본 인덱스 복원
CREATE INDEX idx_orders_status ON orders(status);


-- ============================================
-- 💡 핵심 정리
-- ============================================
-- 1. 복합 인덱스는 컬럼 순서가 중요 (Leftmost Prefix Rule)
-- 2. 카디널리티가 높은(고유값이 많은) 컬럼을 앞에 배치
-- 3. 자주 사용되는 WHERE 조건 패턴을 고려
-- 4. 인덱스가 많으면 쓰기 성능 저하
--
-- 🎯 설계 원칙:
--   - WHERE 절에서 = 조건으로 자주 사용되는 컬럼을 앞에
--   - 범위 조건(>, <, BETWEEN)은 뒤에
--   - ORDER BY에 사용되는 컬럼을 맨 뒤에 고려
