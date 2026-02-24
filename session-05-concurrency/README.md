# [Session 05] 동시성 -- 경쟁 상태 재현과 잠금으로 해결하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- 동시 요청으로 경쟁 상태(Race Condition)를 직접 재현할 수 있다
- 비관적 잠금(`@Lock(PESSIMISTIC_WRITE)`)으로 정합성을 보장할 수 있다
- `SELECT ... FOR UPDATE`가 실제로 어떻게 동작하는지 이해한다
- 각 잠금 전략의 성능 차이를 비교할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- 프로젝트 루트 디렉토리에서 실행

```bash
docker compose up -d
```

실행되는 컨테이너: `grit-mysql`, `grit-redis`, `grit-app`

앱이 준비될 때까지 대기합니다:

```bash
# 헬스체크 통과 확인
curl http://localhost:8080/actuator/health
# 응답: {"status":"UP"}
```

> 포트가 다를 경우 `.env` 파일의 `APP_PORT` 값을 확인하세요. 기본값은 8080입니다.

## 핵심 개념

```
경쟁 상태(Race Condition):

재고 100에서 두 트랜잭션이 동시에 1개씩 차감하는 경우:

트랜잭션 A: SELECT stock FROM menus WHERE id=1  -> 100
트랜잭션 B: SELECT stock FROM menus WHERE id=1  -> 100  (A가 커밋 전에 읽음)
트랜잭션 A: UPDATE menus SET stock = 99 WHERE id=1
트랜잭션 B: UPDATE menus SET stock = 99 WHERE id=1  (100-1, A의 차감이 반영 안 됨)

결과: 2개를 차감했는데 재고가 99 -> 정합성 깨짐!


비관적 잠금(Pessimistic Lock):

트랜잭션 A: SELECT ... FOR UPDATE  -> 잠금 획득
트랜잭션 B: SELECT ... FOR UPDATE  -> A가 커밋할 때까지 대기
트랜잭션 A: UPDATE stock = 99, COMMIT  -> 잠금 해제
트랜잭션 B: SELECT ... FOR UPDATE  -> 이제 99 읽음
트랜잭션 B: UPDATE stock = 98, COMMIT

결과: 정확히 98 -> 정합성 보장!
```

### 코드 구조

```
MenuService.java
├── decreaseStockWithoutLock()      -> 잠금 없음 (L1: 경쟁 상태 재현)
└── decreaseStockWithPessimisticLock()  -> SELECT ... FOR UPDATE (L2: 정합성 보장)

MenuRepository.java
└── findByIdWithPessimisticLock()   -> @Lock(PESSIMISTIC_WRITE)
```

---

## Level 1: 따라하기 -- 경쟁 상태 재현

### Step 1: 초기 재고 확인

```bash
# 메뉴 ID 1번 (후라이드 치킨)의 현재 재고 확인
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
```

예상 출력:
```
+----+--------------+-------+
| id | name         | stock |
+----+--------------+-------+
|  1 | 후라이드 치킨  |   100 |
+----+--------------+-------+
```

### Step 2: 동시 요청으로 재고 차감 (잠금 없음)

아래 명령어는 50개의 curl 요청을 백그라운드에서 동시에 실행합니다.
기대 결과는 재고 50이지만, 경쟁 상태로 인해 실제 결과는 다를 것입니다.

```bash
# 50개의 동시 요청 (각각 1개씩 차감 -> 기대 결과: 재고 50)
for i in $(seq 1 50); do
  curl -s -X POST "http://localhost:8080/api/menus/1/decrease-stock?quantity=1" &
done
wait

# 실제 재고 확인
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
```

예상 출력:
```
+----+--------------+-------+
| id | name         | stock |
+----+--------------+-------+
|  1 | 후라이드 치킨  |    67 |   <- 50이 아닌 더 큰 값
+----+--------------+-------+
```

**결과**: 재고가 50이 아닌 더 큰 값이 남아있습니다. 경쟁 상태로 인해 일부 차감이 누락되었습니다.

**왜 이런 일이 일어나는가?**
여러 트랜잭션이 같은 시점에 재고를 읽고, 각자 -1 한 값을 저장합니다.
서로 상대방의 차감을 모르기 때문에 최종값이 틀어집니다.

### Step 3: 재고 초기화

다음 실험을 위해 재고를 100으로 돌립니다.

```bash
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = 100 WHERE id = 1;"

# 확인
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
```

### Step 4: 앱 로그에서 실행된 쿼리 확인

경쟁 상태가 발생할 때 SQL 로그로 동작을 관찰합니다.

```bash
# 로그에서 UPDATE 쿼리 확인 (잠금 없음 버전)
docker logs grit-app 2>&1 | grep -A 2 "decrease-stock" | tail -20
```

---

## Level 2: 변형하기 -- 비관적 잠금으로 해결

### Step 1: 비관적 잠금 API로 동시 요청

동일한 50개 동시 요청을 비관적 잠금 엔드포인트에 보냅니다.

```bash
# 재고 100 -> 50개 동시 차감 (비관적 잠금 사용)
for i in $(seq 1 50); do
  curl -s -X POST "http://localhost:8080/api/menus/1/decrease-stock-pessimistic?quantity=1" &
done
wait

# 결과 확인: 정확히 50이 남아야 함
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT id, name, stock FROM menus WHERE id = 1;"
```

예상 출력:
```
+----+--------------+-------+
| id | name         | stock |
+----+--------------+-------+
|  1 | 후라이드 치킨  |    50 |   <- 정확히 50!
+----+--------------+-------+
```

**결과**: 정확히 50이 남습니다. `SELECT ... FOR UPDATE`가 동시 접근을 순차 처리합니다.

### Step 2: 실행된 쿼리 비교

앱 로그에서 두 방식의 SQL 차이를 확인합니다.

```bash
docker logs grit-app 2>&1 | grep -i "select\|for update" | tail -20
```

비관적 잠금 버전에서는 아래와 같은 쿼리가 실행됩니다:
```sql
SELECT m.id, m.name, m.stock, ...
FROM menus m
WHERE m.id = 1
FOR UPDATE   -- 이 줄이 핵심! 다른 트랜잭션의 접근을 차단
```

### Step 3: 응답 시간 차이 측정

잠금이 없을 때는 빠르지만 정합성이 깨지고,
잠금이 있을 때는 순서대로 처리되어 시간이 더 걸립니다.

```bash
# 재고 초기화
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = 1000 WHERE id = 1;"

# 잠금 없음: 10개 동시 요청 총 시간 측정
time (for i in $(seq 1 10); do
  curl -s -X POST "http://localhost:8080/api/menus/1/decrease-stock?quantity=1" &
done; wait)

# 재고 초기화
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = 1000 WHERE id = 1;"

# 비관적 잠금: 10개 동시 요청 총 시간 측정
time (for i in $(seq 1 10); do
  curl -s -X POST "http://localhost:8080/api/menus/1/decrease-stock-pessimistic?quantity=1" &
done; wait)
```

**관찰 포인트**: 비관적 잠금은 순차 처리로 인해 총 응답 시간이 길어지지만, 재고 정합성이 보장됩니다.

---

## Level 3: 만들기 -- 한정판 선착순 구매 시스템

### 요구사항

재고 10개의 한정판 메뉴에 100명이 동시에 주문하는 시나리오를 구현하세요:

```
목표:
- 정확히 10명만 주문 성공 (HTTP 200 + "주문 완료" 메시지)
- 나머지 90명에게는 "품절" 응답 (HTTP 409 Conflict)
- 최종 재고는 0이어야 함
```

### 구현 위치

`app/src/main/java/com/gritmoments/backend/menu/service/MenuService.java`에
새 메서드를 추가합니다:

```java
/**
 * 한정판 선착순 차감 (재고 0이면 예외 발생)
 */
@Transactional
public void decreaseStockLimited(Long menuId, int quantity) {
    Menu menu = menuRepository.findByIdWithPessimisticLock(menuId)
            .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));

    // TODO: 재고가 부족하면 적절한 예외를 던지세요
    // 힌트: if (menu.getStock() < quantity) throw new ...
    menu.decreaseStock(quantity);
}
```

### 힌트

- `@Lock(PESSIMISTIC_WRITE)` + 재고 체크 + 예외 처리 조합으로 구현합니다
- 재고 부족 시 HTTP 409를 반환하도록 컨트롤러에서 예외를 처리합니다
- `level3/` 폴더에 스캐폴딩 코드가 있습니다

### 검증

```bash
# 재고를 10으로 설정
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = 10 WHERE id = 1;"

# 100개 동시 요청
SUCCESS=0; FAIL=0
for i in $(seq 1 100); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "http://localhost:8080/api/menus/1/decrease-stock-limited?quantity=1")
  if [ "$STATUS" = "200" ]; then SUCCESS=$((SUCCESS+1))
  else FAIL=$((FAIL+1))
  fi &
done
wait

echo "성공: $SUCCESS, 실패: $FAIL"
# 기대: 성공: 10, 실패: 90

# 최종 재고 확인
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "SELECT stock FROM menus WHERE id = 1;"
# 기대: stock = 0
```

---

## 정리

```bash
# 전체 서비스 종료
docker compose down

# 볼륨까지 삭제 (재고 초기화 포함, 선택)
docker compose down -v
```

## 핵심 정리

| 잠금 전략 | 방식 | 장점 | 단점 | 적합한 경우 |
|----------|------|------|------|------------|
| 잠금 없음 | 일반 SELECT + UPDATE | 가장 빠름 | 동시 요청 시 정합성 깨짐 | 정합성이 중요하지 않을 때 |
| 비관적 잠금 | SELECT ... FOR UPDATE | 강력한 정합성 보장 | DB 락 대기, 처리량 감소 | 충돌이 자주 발생하는 재고/포인트 |
| 낙관적 잠금 | @Version 컬럼 + 재시도 | 충돌 적을 때 효율적 | 충돌 시 재시도 로직 필요 | 충돌이 드문 일반 데이터 수정 |
| 분산 락 | Redis SETNX (Redisson) | 다중 서버 지원 | 복잡성 증가, 락 만료 관리 | 여러 서버가 같은 자원에 접근 |

## 더 해보기 (선택)

- [ ] 낙관적 잠금 구현: `Menu` 엔티티에 `@Version Long version` 필드 추가 후 동일 테스트
- [ ] Redisson 분산 락 실험: `RLock`을 사용하여 단일 DB 락과 성능 비교
- [ ] wrk 또는 ab로 처리량 측정: 잠금 있음/없음 각각 초당 처리 요청 수 비교
- [ ] 데드락 재현: 두 트랜잭션이 서로 다른 순서로 두 행을 잠글 때 발생하는 현상 관찰
