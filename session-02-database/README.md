# [Session 02] 데이터베이스 -- EXPLAIN으로 쿼리 성능 분석하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- EXPLAIN 결과를 읽고 쿼리 성능 문제를 진단할 수 있다
- 인덱스 유무에 따른 성능 차이를 직접 측정할 수 있다
- 복합 인덱스의 컬럼 순서가 성능에 미치는 영향을 이해한다
- N+1 문제를 재현하고 JOIN FETCH로 해결할 수 있다

## 사전 준비

- **`exercises/` 디렉토리**(이 폴더의 상위)에서 실행:
  ```bash
  cd exercises/
  docker compose up -d   # MySQL + App 실행
  ```
- 컨테이너 이름: `grit-app`(앱), `grit-mysql`(DB), `grit-redis`(캐시)
- 시드 데이터가 자동 삽입됨 (users 6건, menus 25건, orders 10만건)
- 데이터 확인: `docker exec -it grit-mysql mysql -uroot -proot1234 backend_study -e "SELECT COUNT(*) FROM orders;"`
- **참고**: 데이터가 10만 건 수준이면 인덱스 유무에 따른 실행 시간 차이가 미미할 수 있습니다. EXPLAIN의 `type`과 `rows` 변화에 집중하세요.

---

## Level 1: 따라하기 -- EXPLAIN으로 인덱스 효과 확인

### Step 1: MySQL 접속

```bash
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study
```

### Step 2: 인덱스 없이 쿼리 실행

```sql
-- 현재 인덱스 확인
SHOW INDEX FROM orders;

-- 인덱스 삭제 (실습용)
ALTER TABLE orders DROP INDEX idx_orders_status;

-- EXPLAIN으로 실행 계획 확인 (인덱스 없음)
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';
-- type: ALL (풀 테이블 스캔), rows: ~100000
```

### Step 3: 인덱스 추가 후 비교

```sql
-- 인덱스 추가
CREATE INDEX idx_orders_status ON orders(status);

-- 동일 쿼리 EXPLAIN (인덱스 있음)
EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED';
-- type: ref (인덱스 스캔), rows: ~20000
```

### Step 4: 실행 시간 비교

```sql
-- 인덱스 없이 (삭제 후)
ALTER TABLE orders DROP INDEX idx_orders_status;
SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';
-- Query time 확인

-- 인덱스 있을 때
CREATE INDEX idx_orders_status ON orders(status);
SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED';
-- Query time 비교
```

---

## Level 2: 변형하기 -- 복합 인덱스와 N+1 문제

### Step 1: 복합 인덱스 순서 실험

```sql
-- 복합 인덱스 (user_id, status)
CREATE INDEX idx_test_user_status ON orders(user_id, status);
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'COMPLETED';

-- 복합 인덱스 (status, user_id) - 순서 변경
CREATE INDEX idx_test_status_user ON orders(status, user_id);
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'COMPLETED';

-- 정리
DROP INDEX idx_test_user_status ON orders;
DROP INDEX idx_test_status_user ON orders;
```

**관찰 포인트**: WHERE 조건의 카디널리티(고유값 수)가 높은 컬럼을 앞에 두는 것이 일반적으로 효율적입니다.

### Step 2: N+1 문제 재현

앱 로그에서 N+1 문제를 확인합니다:

```bash
# 주문 목록 조회 시 N+1 쿼리 발생 확인
curl http://localhost:8080/api/orders/1
docker logs grit-app --tail 30 | grep "Hibernate"
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

---

## 정리

```bash
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
