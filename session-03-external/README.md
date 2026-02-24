# [Session 03] 외부 연동 -- 타임아웃/재시도/서킷브레이커

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- 타임아웃 없이 외부 API를 호출할 때의 위험을 체감한다
- RestTemplate에 타임아웃을 설정할 수 있다
- Spring Retry로 지수 백오프 재시도를 구현할 수 있다
- Resilience4j 서킷 브레이커의 상태 전이를 이해한다
- 멱등키(Idempotency Key)로 이중 결제를 방지할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **프로젝트 루트 디렉토리**에서 실행:
  ```bash
  # docker-compose.yml이 있는 위치 (ai-backend-practice/)
  docker compose --profile external up -d
  ```

실행되는 컨테이너:
- `grit-app` (8080): Spring Boot 애플리케이션
- `grit-mysql` (3306): 데이터베이스
- `grit-redis` (6379): 캐시
- `grit-mock-pg` (9000): 모의 PG(Payment Gateway) 서버

모의 PG 서버 동작 (환경 변수 기준):
- **50%**: 즉시 성공 응답
- **30%**: 1~3초 지연 후 성공
- **20%**: 500 에러 반환

앱 포트는 기본 8080입니다. mock-pg 포트는 고정 9000입니다.

---

## Level 1: 따라하기 -- 타임아웃 설정

### Step 1: 환경 확인

```bash
# 앱 서버 상태 확인
curl http://localhost:8080/actuator/health
# 기대 출력: {"status":"UP",...}

# 모의 PG 서버 헬스체크
curl http://localhost:9000/health
# 기대 출력: {"status":"ok","message":"Mock PG Server is running"}

# 컨테이너 상태 확인
docker ps | grep grit
# grit-app, grit-mysql, grit-redis, grit-mock-pg 모두 Up 상태여야 합니다
```

### Step 2: 모의 PG 서버의 랜덤 동작 체험

```bash
# 결제 요청 (랜덤 지연/실패)
curl -X POST http://localhost:9000/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "amount": 25000, "idempotencyKey": "test-001"}'
# 성공 기대 출력: {"status":"SUCCESS","transactionId":"pg-tx-abc123","amount":25000}
# 실패 기대 출력: {"error":"Payment processing failed","code":"PG_ERROR"}
```

```bash
# 10번 반복 호출로 성공/실패/지연 패턴 확인
for i in $(seq 1 10); do
  result=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:9000/api/payments \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": $i, \"amount\": 10000, \"idempotencyKey\": \"test-key-$i\"}")
  echo "요청 $i: HTTP $result"
done
# 기대 출력 (랜덤):
# 요청 1: HTTP 200  <- 성공
# 요청 2: HTTP 200  <- 성공 (지연 있었을 수 있음)
# 요청 3: HTTP 500  <- 실패
# 요청 4: HTTP 200
# ...
```

### Step 3: 응답 시간 측정 -- 타임아웃의 필요성 체감

```bash
# 응답 시간 측정 (최대 3초 지연 가능)
for i in $(seq 1 10); do
  curl -s -o /dev/null \
    -w "요청 $i: %{time_total}s (HTTP %{http_code})\n" \
    -X POST http://localhost:9000/api/payments \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": $i, \"amount\": 10000, \"idempotencyKey\": \"latency-test-$i\"}"
done
# 기대 출력 (랜덤):
# 요청 1: 0.012s (HTTP 200)   <- 즉시 성공
# 요청 2: 2.341s (HTTP 200)   <- 지연 후 성공 (서버 과부하 시뮬레이션)
# 요청 3: 0.011s (HTTP 500)   <- 즉시 실패
# 요청 4: 1.087s (HTTP 200)
# ...
```

**관찰 포인트**: 타임아웃이 없으면 2~3초씩 걸리는 요청이 스레드를 점유합니다. 트래픽이 몰리면 스레드 풀이 소진되어 다른 정상 요청도 처리하지 못합니다.

### Step 4: 앱의 타임아웃 설정 확인

`RestTemplateConfig.java`에서 RestTemplate의 타임아웃을 설정합니다:

```java
// app/src/main/java/com/gritmoments/backend/common/config/RestTemplateConfig.java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofSeconds(3))  // 연결 타임아웃: 3초
        .setReadTimeout(Duration.ofSeconds(5))      // 읽기 타임아웃: 5초
        .build();
}
```

### Step 5: 앱을 통한 결제 요청

```bash
# 먼저 주문을 생성합니다 (결제 요청에는 주문 ID가 필요)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
# 기대 출력: {"success":true,"data":{"id":1,"status":"PENDING","totalAmount":16000,...}}
# 반환된 주문 ID를 기억하세요 (예: 1)

# 앱을 통해 결제 요청 (앱 -> mock-pg 호출)
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "amount": 16000, "idempotencyKey": "order-1-pay-001"}'
# 성공 기대 출력: {"success":true,"data":{"id":1,"status":"SUCCESS","pgTransactionId":"pg-tx-..."}}
# 실패 시: {"success":false,"message":"결제 처리 중 오류가 발생했습니다: PG 결제 승인 실패"}
```

```bash
# 앱 로그에서 PG 호출 과정 확인
docker logs grit-app --tail 30 | grep -E "\[결제|PG"
# 기대 출력:
# [결제 요청 시작] 주문 ID: 1, 금액: 16000, 멱등키: order-1-pay-001
# [PG 요청 시작] URL: http://mock-pg:9000/api/payments, 주문 ID: 1, 금액: 16000
# [PG 응답 성공] 트랜잭션 ID: pg-tx-abc123
# [결제 성공] Payment ID: 1, PG 트랜잭션 ID: pg-tx-abc123
```

### Step 6: 멱등키로 중복 결제 방지 확인

```bash
# 동일한 멱등키로 다시 결제 요청
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "amount": 16000, "idempotencyKey": "order-1-pay-001"}'
# 기대 출력: 새 결제가 생성되지 않고 기존 결제 결과를 반환

# 앱 로그에서 중복 감지 확인
docker logs grit-app --tail 10 | grep "중복"
# 기대 출력: [중복 결제 감지] 멱등키: order-1-pay-001, 기존 결제 ID: 1
```

---

## Level 2: 변형하기 -- Spring Retry 지수 백오프

### Step 1: 현재 재시도 설정 확인

`application.yml`에서 Resilience4j retry 설정을 확인합니다:

```yaml
resilience4j:
  retry:
    instances:
      paymentService:
        max-attempts: 3             # 최대 3번 시도 (첫 시도 포함)
        wait-duration: 1s           # 재시도 간격 1초
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2  # 지수 배율 (1s -> 2s -> 실패)
```

### Step 2: 재시도 동작 확인

결제 요청을 보내고 로그에서 재시도 패턴을 관찰합니다:

```bash
# 새 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 2, "restaurantId": 1, "items": [{"menuId": 2, "quantity": 1}]}'
# 주문 ID를 기억하세요 (예: 2)

# 결제 요청 (실패율 20%이므로 재시도가 발생할 수 있음)
time curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 2, "amount": 8000, "idempotencyKey": "order-2-pay-001"}'

# 로그에서 재시도 여부 확인
docker logs grit-app --tail 30 | grep -E "\[결제|PG|Retry"
# 재시도가 발생한 경우 기대 출력:
# [PG 요청 시작] URL: http://mock-pg:9000/api/payments, 주문 ID: 2, ...
# [PG 요청 시작] URL: http://mock-pg:9000/api/payments, 주문 ID: 2, ...  <- 재시도
# [PG 응답 성공] 트랜잭션 ID: pg-tx-xyz789
```

### Step 3: 재시도 횟수와 간격 변경

`app/src/main/resources/application.yml` 수정:

```yaml
resilience4j:
  retry:
    instances:
      paymentService:
        max-attempts: 5          # 3 -> 5로 변경
        wait-duration: 500ms     # 1s -> 500ms로 변경
```

앱을 재시작하고 동일한 테스트를 반복합니다:

```bash
# 프로젝트 루트에서 실행
docker compose up -d --build app

# 재시도 5번 설정으로 성공률이 높아지는지 확인
for i in $(seq 1 5); do
  curl -s -o /dev/null -w "결제 $i: HTTP %{http_code}, 소요 %{time_total}s\n" \
    -X POST http://localhost:8080/api/payments \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": $i, \"amount\": 10000, \"idempotencyKey\": \"retry-test-$i\"}"
done
```

**관찰 포인트**: 재시도 횟수가 많을수록 성공률이 높아지지만, 응답 시간도 길어집니다. 지수 백오프는 서버에 부하가 집중되는 것을 막아줍니다.

---

## Level 3: 만들기 -- Resilience4j 서킷 브레이커

### 서킷 브레이커 동작 원리

```
CLOSED (정상)
  │ 실패율이 임계값(50%)을 초과하면
  v
OPEN (장애)
  │ wait-duration(10s) 경과 후
  v
HALF-OPEN (복구 확인)
  │ 3번의 요청 중 성공률이 50% 이상이면
  v
CLOSED (복구)
```

### Step 1: 서킷 브레이커 현재 상태 확인

```bash
# Actuator로 서킷 브레이커 상태 확인
curl http://localhost:8080/actuator/health | python3 -m json.tool
# 기대 출력 (health 섹션):
# "circuitBreakers": {
#   "paymentService": {
#     "status": "UP",
#     "details": {
#       "state": "CLOSED",
#       "failureRate": "-1.0%",
#       "bufferedCalls": 0
#     }
#   }
# }
```

### Step 2: 서킷 브레이커 OPEN 상태 유도

mock-pg의 실패율을 높여 서킷 브레이커가 열리도록 합니다.

docker-compose.yml에서 mock-pg의 `FAILURE_RATE`를 임시로 높입니다:

```yaml
mock-pg:
  environment:
    FAILURE_RATE: "0.9"   # 90% 실패율로 변경
    MAX_DELAY_MS: "100"
```

```bash
# 설정 적용 후 재시작
docker compose --profile external up -d --force-recreate mock-pg

# 결제 요청을 10번 이상 반복하여 실패율 축적
for i in $(seq 1 15); do
  curl -s -o /dev/null \
    -X POST http://localhost:8080/api/payments \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": $i, \"amount\": 5000, \"idempotencyKey\": \"cb-test-$i\"}" &
done
wait

# 서킷 브레이커 상태 확인
curl http://localhost:8080/actuator/health | python3 -m json.tool
# 기대 출력: "state": "OPEN"  <- 서킷 브레이커가 열림

# OPEN 상태에서 결제 요청 시 즉시 실패 (mock-pg 호출 없음)
time curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 99, "amount": 5000, "idempotencyKey": "cb-open-test"}'
# 기대 출력: 즉시 실패 (응답 시간 0.01s 이하) - 서킷 브레이커가 차단
# 로그: [서킷 브레이커 동작] PG 시스템 장애 - 주문 ID: 99
```

### Step 3: HALF-OPEN 상태 전이 확인

```bash
# wait-duration(10초) 동안 대기
sleep 11

# mock-pg를 정상 상태로 복원
# docker-compose.yml에서 FAILURE_RATE: "0.2"로 되돌리고
docker compose --profile external up -d --force-recreate mock-pg

# HALF-OPEN: 일부 요청만 통과 (permitted-number-of-calls-in-half-open-state: 3)
for i in $(seq 1 3); do
  curl -s -o /dev/null -w "복구 테스트 $i: HTTP %{http_code}\n" \
    -X POST http://localhost:8080/api/payments \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": $i, \"amount\": 5000, \"idempotencyKey\": \"recovery-$i\"}"
done

# 복구 후 상태 확인
curl http://localhost:8080/actuator/health | python3 -m json.tool
# 기대 출력: "state": "CLOSED"  <- 서킷 브레이커 복구됨
```

### Step 4: 스캐폴딩 코드 활용

`level3/` 폴더에 두 가지 스캐폴딩 파일이 있습니다:

**`PaymentServiceWithRetry.java`**: Spring Retry의 `@Retryable`을 직접 구현합니다.
- TODO 1~6을 채워서 타임아웃, 재시도, 폴백을 완성하세요.
- `@Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))`

**`CircuitBreakerTest.java`**: 서킷 브레이커 상태 전이를 JUnit으로 테스트합니다.
- TODO 1~16을 채워서 CLOSED -> OPEN -> HALF_OPEN -> CLOSED 전이를 검증하세요.
- 핵심 설정값 (`failure-rate-threshold: 50`, `wait-duration-in-open-state: 10s`, `permitted-number-of-calls-in-half-open-state: 3`)을 application.yml에서 확인하고 테스트에 반영하세요.

---

## 정리

```bash
# 프로젝트 루트에서 실행
docker compose --profile external down
```

## 핵심 정리

| 패턴 | 목적 | Spring/라이브러리 |
|------|------|-----------------|
| 타임아웃 | 무한 대기 방지 | `RestTemplateBuilder.setConnectTimeout()`, `setReadTimeout()` |
| 재시도 | 일시적 실패 복구 | `@Retry(name = "paymentService")`, `@Retryable` |
| 지수 백오프 | 재시도 시 서버 부하 분산 | `@Backoff(delay = 1000, multiplier = 2.0)` |
| 서킷브레이커 | 장애 확산 방지 | `@CircuitBreaker(name = "paymentService", fallbackMethod = "...")` |
| 멱등키 | 이중 처리 방지 | DB unique key + 요청 전 중복 확인 |

| 서킷 브레이커 상태 | 동작 |
|------------------|------|
| CLOSED | 정상. 모든 요청 통과 |
| OPEN | 장애 감지. 모든 요청 즉시 실패 (외부 호출 차단) |
| HALF-OPEN | 복구 확인 중. 일부 요청만 통과하여 성공률 측정 |

## 더 해보기 (선택)

- [ ] Actuator에서 실시간 서킷 브레이커 메트릭 확인: `GET /actuator/circuitbreakers`
- [ ] `FAILURE_RATE` 환경변수를 `1.0`(항상 실패)으로 바꾸고 서킷 브레이커가 얼마나 빠르게 열리는지 확인
- [ ] `wait-duration-in-open-state`를 30s로 늘려 OPEN 상태가 유지되는 시간 변화 관찰
- [ ] Resilience4j TimeLimiter: `@TimeLimiter`로 메서드 실행 시간 자체를 제한하는 방법 알아보기
