-- [Session 02 - Level 3] 이커머스 주문 시스템 스키마 설계
--
-- 요구사항:
-- 1. 이커머스 주문 시스템의 핵심 테이블 설계
-- 2. 자주 실행될 쿼리를 예상하고 적절한 인덱스 설계
-- 3. EXPLAIN으로 설계한 인덱스의 효과 검증

-- ============================================
-- TODO 1: 테이블 설계
-- ============================================

-- 사용자 테이블 (이미 존재)
-- users: id, name, email, phone, created_at

-- 상품 테이블
-- TODO: products 테이블을 설계하세요
--   컬럼: id, name, category, price, stock, created_at
--   힌트: 어떤 컬럼에 인덱스가 필요할까요?

-- 주문 테이블 (이미 존재하지만 개선 필요)
-- orders: id, user_id, restaurant_id, status, total_amount, created_at

-- 주문 상세 테이블
-- TODO: order_items 테이블을 설계하세요
--   컬럼: id, order_id, menu_id, quantity, price
--   힌트: 외래 키와 인덱스를 고려하세요


-- ============================================
-- TODO 2: 자주 실행될 쿼리 예상
-- ============================================

-- 예상 쿼리 1: 특정 사용자의 주문 목록 조회 (최신순)
-- TODO: 이 쿼리를 작성하세요
-- SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 10;
-- 필요한 인덱스: ???


-- 예상 쿼리 2: 특정 카테고리의 상품 검색 (가격 범위 필터링)
-- TODO: 이 쿼리를 작성하세요
-- SELECT * FROM products WHERE category = ? AND price BETWEEN ? AND ? ORDER BY price ASC;
-- 필요한 인덱스: ???


-- 예상 쿼리 3: 특정 주문의 상세 항목 조회
-- TODO: 이 쿼리를 작성하세요
-- SELECT * FROM order_items WHERE order_id = ?;
-- 필요한 인덱스: ???


-- 예상 쿼리 4: 특정 기간의 완료된 주문 통계
-- TODO: 이 쿼리를 작성하세요
-- SELECT COUNT(*), SUM(total_amount)
-- FROM orders
-- WHERE status = 'COMPLETED' AND created_at BETWEEN ? AND ?;
-- 필요한 인덱스: ???


-- ============================================
-- TODO 3: 인덱스 설계 및 생성
-- ============================================

-- TODO: 위에서 예상한 쿼리들을 효율적으로 실행하기 위한 인덱스를 설계하세요
--
-- 예시:
-- CREATE INDEX idx_orders_user_created ON orders(user_id, created_at);
-- CREATE INDEX idx_products_category_price ON products(category, price);
--
-- 💡 고려 사항:
--   1. 복합 인덱스의 컬럼 순서
--   2. WHERE, ORDER BY, JOIN에 사용되는 컬럼
--   3. 카디널리티 (고유값이 많은 컬럼을 앞에)
--   4. 인덱스 개수 (너무 많으면 쓰기 성능 저하)


-- ============================================
-- TODO 4: EXPLAIN으로 검증
-- ============================================

-- TODO: 위에서 작성한 쿼리들을 EXPLAIN으로 실행하여 인덱스가 잘 사용되는지 확인하세요
--
-- 예시:
-- EXPLAIN SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC LIMIT 10;
--
-- 💡 확인 사항:
--   - type이 ALL이 아닌지
--   - key 필드에 생성한 인덱스가 표시되는지
--   - rows가 합리적인 수준인지
--   - Extra에 'Using filesort'가 없는지 (정렬에 인덱스 사용)


-- ============================================
-- TODO 5: 실제 데이터로 성능 비교
-- ============================================

-- TODO: 인덱스 있을 때와 없을 때의 쿼리 실행 시간을 비교하세요
--
-- 단계:
-- 1. 인덱스 삭제
-- 2. 쿼리 실행 시간 측정
-- 3. 인덱스 생성
-- 4. 동일 쿼리 실행 시간 측정
-- 5. 결과 비교


-- ============================================
-- 💡 설계 체크리스트
-- ============================================
-- [ ] PRIMARY KEY는 모든 테이블에 있는가?
-- [ ] 외래 키에 인덱스가 있는가? (JOIN 성능)
-- [ ] WHERE 절에 자주 사용되는 컬럼에 인덱스가 있는가?
-- [ ] 복합 인덱스의 컬럼 순서가 쿼리 패턴에 맞는가?
-- [ ] 불필요한 인덱스는 없는가? (쓰기 성능 고려)
-- [ ] EXPLAIN으로 모든 주요 쿼리를 검증했는가?


-- ============================================
-- 💡 추가 도전 과제
-- ============================================
-- 1. N+1 문제 재현: 주문 목록 조회 시 각 주문의 사용자 정보를 함께 조회
--    해결: JOIN FETCH 또는 @EntityGraph 사용
--
-- 2. 커버링 인덱스: 인덱스만으로 쿼리를 완전히 처리 (테이블 접근 없이)
--    힌트: SELECT 절의 컬럼을 모두 인덱스에 포함
--
-- 3. 파티셔닝: created_at 기준으로 주문 테이블을 월별로 파티셔닝
--    힌트: PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at))
