# [Session 05] 동시성 -- 경쟁 상태 재현과 잠금으로 해결하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- 동시 요청으로 경쟁 상태(Race Condition)를 직접 재현할 수 있다
- 비관적 잠금(`@Lock(PESSIMISTIC_WRITE)`)으로 정합성을 보장할 수 있다
- Redisson 분산 락의 동작을 이해한다
- 각 잠금 전략의 성능 차이를 비교할 수 있다

## 사전 준비

**중요**: `exercises/` 디렉토리(상위 폴더)에서 Docker Compose 실행

```bash
cd exercises/
docker compose up -d
```

실행되는 컨테이너: `grit-mysql`, `grit-redis`, `grit-app`

---

## Level 1: 따라하기 -- 경쟁 상태 재현

### Step 1: 초기 재고 확인

```bash
# 메뉴 ID 1번 (후라이드 치킨)의 현재 재고 확인
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
# stock = 100
```

### Step 2: 동시 요청으로 재고 차감 (잠금 없음)

```bash
# 50개의 동시 요청 (각각 1개씩 차감 -> 기대 결과: 재고 50)
for i in $(seq 1 50); do
  curl -s -X POST "http://localhost:8080/api/menus/1/decrease-stock?quantity=1" &
done
wait

# 실제 재고 확인
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
```

**결과**: 재고가 50이 아닌 더 큰 값이 남아있습니다! 경쟁 상태로 인해 일부 차감이 누락되었습니다.

### Step 3: 재고 초기화

```bash
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = 100 WHERE id = 1;"
```

---

## Level 2: 변형하기 -- 비관적 잠금으로 해결

### Step 1: 비관적 잠금 API로 동시 요청

```bash
# 재고 100 -> 50개 동시 차감 (비관적 잠금 사용)
for i in $(seq 1 50); do
  curl -s -X POST "http://localhost:8080/api/menus/1/decrease-stock-pessimistic?quantity=1" &
done
wait

# 결과 확인: 정확히 50이 남아야 함
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
```

**결과**: 정확히 50이 남습니다. `SELECT ... FOR UPDATE`가 동시 접근을 순차 처리합니다.

---

## Level 3: 만들기 -- 한정판 선착순 구매 시스템

### 요구사항

재고 10개의 한정판 메뉴에 100명이 동시에 주문하는 시나리오:
- 정확히 10명만 주문 성공
- 나머지 90명에게는 "품절" 응답
- Redisson 분산 락 또는 비관적 잠금 사용

---

## 정리

```bash
docker compose down
```

## 핵심 정리

| 잠금 전략 | 방식 | 장점 | 단점 | 적합한 경우 |
|----------|------|------|------|------------|
| 잠금 없음 | - | 빠름 | 정합성 깨짐 | 정합성이 중요하지 않을 때 |
| 비관적 잠금 | SELECT ... FOR UPDATE | 강력한 정합성 보장 | DB 락 대기 발생 | 충돌이 자주 발생할 때 |
| 낙관적 잠금 | @Version 컬럼 | 충돌 적을 때 효율적 | 충돌 시 재시도 필요 | 충돌이 드물 때 |
| 분산 락 | Redis SETNX | 다중 서버 지원 | 복잡성 증가 | 여러 서버가 같은 자원 접근 |
