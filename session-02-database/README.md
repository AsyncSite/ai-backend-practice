# [Session 02] 데이터베이스 -- EXPLAIN으로 쿼리 성능 분석하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- EXPLAIN 결과를 읽고 쿼리 성능 문제를 진단할 수 있다
- 인덱스 유무에 따른 성능 차이를 직접 측정할 수 있다
- 복합 인덱스의 컬럼 순서가 성능에 미치는 영향을 이해한다
- N+1 문제를 재현하고 JOIN FETCH로 해결할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **프로젝트 루트 디렉토리**에서 실행:
  ```bash
  # docker-compose.yml이 있는 위치 (ai-backend-practice/)
  docker compose up -d
  ```
- 컨테이너 이름: `grit-app`(앱), `grit-mysql`(DB), `grit-redis`(캐시)
- 시드 데이터가 자동 삽입됩니다 (restaurants 5건, menus 25건, users 6건, orders 10만 건)
- 데이터 확인:
  ```bash
  docker exec -it grit-mysql mysql -uroot -proot1234 backend_study \
    -e "SELECT COUNT(*) FROM orders;"
  # 기대 출력: COUNT(*) = 100000 (또는 그 이상)
  ```
- **참고**: 데이터가 10만 건 수준이면 인덱스 유무에 따른 실행 시간 차이가 미미할 수 있습니다. EXPLAIN의 `type`과 `rows` 변화에 집중하세요.

---

## Level 1: 따라하기 -- EXPLAIN으로 인덱스 효과 확인

### Step 1: MySQL 접속

```bash
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study
```

### Step 2: 현재 인덱스 상태 확인

```sql
-- orders 테이블의 인덱스 목록 확인
SHOW INDEX FROM orders;
-- 기대 출력 (일부):
-- Table  Key_name                Column_name
-- orders PRIMARY                 id
-- orders idx_orders_user         user_id
-- orders idx_orders_restaurant   restaurant_id
-- orders idx_orders_user_created user_id
-- orders idx_orders_status       status
```

### Step 3: 인덱스 없이 쿼리 실행

```sql
-- 실습을 위해 status 인덱스를 삭제
ALTER TABLE orders DROP INDEX idx_orders_status;

-- EXPLAIN으로 실행 계획 확인 (인덱스 없음)
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';
```

기대 출력:
```
+----+-------------+--------+------+---------------+------+---------+------+--------+-------------+
| id | select_type | table  | type | possible_keys | key  | key_len | ref  | rows   | Extra       |
+----+-------------+--------+------+---------------+------+---------+------+--------+-------------+
|  1 | SIMPLE      | orders | ALL  | NULL          | NULL | NULL    | NULL | 100000 | Using where |
+----+-------------+--------+------+---------------+------+---------+------+--------+-------------+
```

- `type: ALL` = 풀 테이블 스캔 (가장 느린 방식)
- `rows: ~100000` = 10만 건 전체를 읽어야 함

### Step 4: 인덱스 추가 후 비교

```sql
-- 인덱스 추가
CREATE INDEX idx_orders_status ON orders(status);

-- 동일 쿼리 EXPLAIN (인덱스 있음)
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';
```

기대 출력:
```
+----+-------------+--------+------+-------------------+-------------------+---------+-------+-------+-------+
| id | select_type | table  | type | possible_keys     | key               | key_len | ref   | rows  | Extra |
+----+-------------+--------+------+-------------------+-------------------+---------+-------+-------+-------+
|  1 | SIMPLE      | orders | ref  | idx_orders_status | idx_orders_status | 82      | const | 20000 | NULL  |
+----+-------------+--------+------+-------------------+-------------------+---------+-------+-------+-------+
```

- `type: ref` = 인덱스 참조 (빠름)
- `rows: ~20000` = 필요한 행만 읽음 (전체의 20%)

### Step 5: 실행 시간 직접 비교

```sql
-- 프로파일링 활성화
SET profiling = 1;

-- 인덱스 없이 실행
ALTER TABLE orders DROP INDEX idx_orders_status;
SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';

-- 인덱스 있을 때 실행
CREATE INDEX idx_orders_status ON orders(status);
SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';

-- 실행 시간 확인
SHOW PROFILES;
-- 기대 출력 (예시):
-- Query_ID  Duration   Query
-- 1         0.08532    SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'  <- 인덱스 없음
-- 2         0.00341    SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'  <- 인덱스 있음
```

---

## Level 2: 변형하기 -- 복합 인덱스와 N+1 문제

### Step 1: 복합 인덱스 순서 실험

```sql
-- 실험 전 기존 단일 인덱스 제거
DROP INDEX idx_orders_status ON orders;

-- 복합 인덱스 (user_id 앞, status 뒤)
-- user_id 카디널리티(고유값 수)가 높으므로 앞에 두는 것이 효율적
CREATE INDEX idx_test_user_status ON orders(user_id, status);
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'COMPLETED';
-- 기대: type = ref, rows가 적음

-- 복합 인덱스 (status 앞, user_id 뒤) - 순서 변경
CREATE INDEX idx_test_status_user ON orders(status, user_id);
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'COMPLETED';
-- 기대: type = ref이지만 rows가 다를 수 있음

-- 실험 정리 (원래 인덱스 복원)
DROP INDEX idx_test_user_status ON orders;
DROP INDEX idx_test_status_user ON orders;
CREATE INDEX idx_orders_status ON orders(status);
```

**관찰 포인트**: `user_id`는 사용자 수만큼 고유값을 가지지만, `status`는 6가지 값뿐입니다. 카디널리티(고유값 수)가 높은 컬럼을 복합 인덱스 앞에 두면 더 효율적으로 데이터를 걸러냅니다.

### Step 2: EXPLAIN으로 N+1 문제 확인

애플리케이션 레벨에서 N+1을 관찰합니다.

```bash
# JPA 쿼리 로그가 활성화되어 있으므로 주문 상세 조회 시 발행되는 SQL 확인
curl http://localhost:8080/api/orders/1
docker logs grit-app --tail 50 | grep "Hibernate\|select"
```

`OrderRepository.findByIdWithDetails()`는 JOIN FETCH를 사용하여 N+1을 방지합니다:

```sql
-- 앱 코드에서 사용하는 쿼리 (N+1 없음)
SELECT o FROM Order o
JOIN FETCH o.user
JOIN FETCH o.restaurant
LEFT JOIN FETCH o.items i
LEFT JOIN FETCH i.menu
WHERE o.id = :id
```

**N+1이 발생하는 경우와 해결 방법 비교:**

```sql
-- N+1 발생 예 (Lazy 로딩으로 연관 엔티티를 별도 쿼리로 조회)
-- 주문 1건 조회 쿼리 1번 + 각 OrderItem마다 menu 조회 쿼리 N번 = 1+N번

-- N+1 해결: JOIN FETCH (1번의 쿼리로 모두 조회)
SELECT * FROM orders o
  JOIN users u ON o.user_id = u.id
  JOIN restaurants r ON o.restaurant_id = r.id
  LEFT JOIN order_items oi ON oi.order_id = o.id
  LEFT JOIN menus m ON oi.menu_id = m.id
WHERE o.id = 1;
```

MySQL에서 직접 실행하여 비교해 보세요:

```bash
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study
```

```sql
-- N+1 방식 (비효율)
SET profiling = 1;
SELECT * FROM orders WHERE id = 1;
SELECT * FROM order_items WHERE order_id = 1;
SELECT * FROM menus WHERE id = 1;  -- item 수만큼 반복
SHOW PROFILES;

-- JOIN FETCH 방식 (효율)
SELECT o.*, u.*, r.*, oi.*, m.*
FROM orders o
JOIN users u ON o.user_id = u.id
JOIN restaurants r ON o.restaurant_id = r.id
LEFT JOIN order_items oi ON oi.order_id = o.id
LEFT JOIN menus m ON oi.menu_id = m.id
WHERE o.id = 1;
SHOW PROFILES;
```

---

## Level 3: 만들기 -- DB 스키마 설계

### 요구사항

이커머스 주문 시스템의 DB 스키마를 직접 설계하세요:
- 어떤 쿼리가 자주 실행될지 예상
- 각 쿼리에 필요한 인덱스 설계
- EXPLAIN으로 설계한 인덱스의 효과 검증

### 파일 안내

`level3/` 폴더에 두 가지 접근 방식이 있습니다:
- **`IndexDesignTask.sql` (가이드형, 추천)**: 섹션별 가이드와 힌트가 포함되어 있어 단계적으로 진행할 수 있습니다
- **`schema-design.sql` (도전형)**: TODO만 제공됩니다. 가이드 없이 직접 설계하고 싶을 때 사용하세요

### 검증 절차

설계한 스키마에 대해 다음 쿼리의 EXPLAIN 결과를 확인하세요:

```sql
-- 1. 특정 사용자의 최근 주문 목록 (페이지네이션)
EXPLAIN SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC LIMIT 10;

-- 2. 특정 상태의 주문 수 집계
EXPLAIN SELECT status, COUNT(*) FROM orders GROUP BY status;

-- 3. 가게별 총 주문 금액
EXPLAIN SELECT restaurant_id, SUM(total_amount)
FROM orders
WHERE status = 'COMPLETED'
GROUP BY restaurant_id;
```

모든 쿼리에서 `type`이 `ALL`이 아닌지 확인하세요.

---

## 정리

```bash
# MySQL 프로파일링 비활성화 (MySQL 세션 내)
# SET profiling = 0;

# 환경 종료 (프로젝트 루트에서)
docker compose down
```

## 핵심 정리

| EXPLAIN type | 의미 | 성능 |
|-------------|------|------|
| ALL | 풀 테이블 스캔 | 나쁨 |
| index | 인덱스 풀 스캔 | 보통 |
| range | 인덱스 범위 스캔 | 좋음 |
| ref | 인덱스 참조 | 좋음 |
| const | 상수 참조 (PK) | 최고 |

| 개념 | 내용 |
|------|------|
| 인덱스 | 특정 컬럼으로 빠르게 검색할 수 있는 자료구조 (B-Tree). 쓰기 성능과 트레이드오프 |
| 복합 인덱스 | 2개 이상 컬럼의 조합 인덱스. 카디널리티가 높은 컬럼을 앞에 |
| N+1 문제 | ORM에서 연관 엔티티를 Lazy 로딩 시 쿼리가 N+1번 발생하는 문제 |
| JOIN FETCH | JPA에서 연관 엔티티를 한 번의 쿼리로 함께 조회하는 방법 |

## 더 해보기 (선택)

- [ ] `EXPLAIN ANALYZE`로 실제 실행 시간 분석 (MySQL 8.0.18+)
- [ ] `SHOW STATUS LIKE 'Handler_read%'`로 인덱스 사용 여부 확인
- [ ] 커버링 인덱스: SELECT 하는 컬럼까지 인덱스에 포함하여 테이블 조회 없애기
- [ ] `FORCE INDEX`로 특정 인덱스 강제 사용
