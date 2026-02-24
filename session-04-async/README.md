# [Session 04] 비동기 처리 -- RabbitMQ로 알림 시스템 구축하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- 동기/비동기 방식의 응답 시간 차이를 직접 측정할 수 있다
- RabbitMQ에 메시지를 발행(Publish)하고 소비(Consume)하는 코드를 작성할 수 있다
- Worker 수에 따른 처리 속도 변화를 관찰할 수 있다
- Dead Letter Queue(DLQ)로 실패한 메시지를 관리할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **프로젝트 루트 디렉토리**에서 실행:
  ```bash
  # docker-compose.yml이 있는 위치 (ai-backend-practice/)
  docker compose --profile async up -d
  ```
- 실행되는 컨테이너: `grit-mysql`, `grit-redis`, `grit-rabbitmq`, `grit-app`
- RabbitMQ Management UI: http://localhost:15672 (ID: guest / PW: guest)
- 앱 포트는 기본 8080입니다.

## 핵심 개념

```
동기 처리 (Synchronous):
  주문 요청 -> DB 저장 (50ms) -> 알림 발송 (500ms) -> 응답 (총 550ms)
  사용자는 알림 발송이 끝날 때까지 대기

비동기 처리 (Asynchronous):
  주문 요청 -> DB 저장 (50ms) -> 메시지 발행 (1ms) -> 응답 (총 51ms)
                                      ↓
                            RabbitMQ Queue (order.notification.queue)
                                      ↓
                            Worker가 별도로 알림 발송 (500ms)
  사용자는 즉시 응답 받고, 알림은 백그라운드에서 처리
```

현재 코드에서의 흐름:
- `OrderController.createOrder()` -> 주문 저장 후 `NotificationService.publishOrderCreatedEvent()` 호출
- `NotificationService`는 `order.exchange`에 `order.created` 라우팅 키로 메시지 발행
- `@RabbitListener(queues = "order.notification.queue")`가 메시지를 소비하여 알림 처리

---

## Level 1: 따라하기 -- 메시지 발행/소비 기본 동작

### Step 1: 환경 확인

```bash
# 앱 서버 상태 확인
curl http://localhost:8080/actuator/health
# 기대 출력: {"status":"UP",...}

# RabbitMQ 컨테이너 상태 확인
docker ps | grep rabbitmq
# 기대 출력: grit-rabbitmq ... Up

# RabbitMQ 연결 확인
docker exec grit-rabbitmq rabbitmqctl status | grep -E "RabbitMQ|Erlang"
# 기대 출력: RabbitMQ 버전 및 상태 정보 출력
```

### Step 2: RabbitMQ 관리 UI 확인

브라우저에서 http://localhost:15672 접속 (ID: guest, PW: guest)

1. **Queues 탭** 클릭
   - `order.notification.queue` 확인 (앱 시작 시 자동 생성)
   - `order.notification.dlq` 확인 (DLQ도 함께 생성됨)
   - **Ready**: 처리 대기 중인 메시지 수
   - **Unacked**: Worker가 처리 중인 메시지 수 (아직 ACK 안 됨)
   - **Total**: 전체 메시지 수

2. **Exchanges 탭** 클릭
   - `order.exchange` 확인
   - Type: `direct` (라우팅 키로 정확한 큐에 전달)

3. **Connections 탭** 클릭
   - 앱이 RabbitMQ에 연결된 것을 확인

### Step 3: 비동기 방식으로 주문 생성 (메시지 발행)

```bash
# 주문 생성 (비동기 알림 발행 포함)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -w "\n응답시간: %{time_total}s\n" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
# 기대 출력:
# {"success":true,"data":{"id":1,"status":"PENDING","totalAmount":16000,...}}
# 응답시간: 0.052s  <- DB 저장 + 메시지 발행만 하므로 빠름
```

```bash
# 5번 반복하여 평균 응답 시간 측정
for i in $(seq 1 5); do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -w "비동기 요청 $i: %{time_total}s\n" \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
done
# 기대 출력:
# 비동기 요청 1: 0.053s
# 비동기 요청 2: 0.048s
# 비동기 요청 3: 0.051s
# 비동기 요청 4: 0.049s
# 비동기 요청 5: 0.050s
```

### Step 4: 앱 로그에서 메시지 발행 확인

```bash
# 메시지 발행 로그 확인
docker logs grit-app --tail 30 | grep -E "\[알림|주문 생성"
# 기대 출력:
# [주문 생성] 사용자: 1, 가게: 1, 항목 수: 1
# [주문 생성 완료] 주문 ID: 1, 총액: 16000원
# [알림 발행] 주문 1 생성 이벤트를 큐에 발행합니다.
# [알림 발행 완료] 주문 1 이벤트가 큐에 저장되었습니다.
```

### Step 5: RabbitMQ Queue에서 메시지 확인

```bash
# Queue에 쌓인 메시지 수 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력 (처리 중이거나 이미 처리되었으면 0):
# name                          messages
# order.notification.queue      0
# order.notification.dlq        0
```

메시지가 빠르게 소비되면 이미 0이 됩니다. 더 많이 쌓으려면 아래 Step 7을 먼저 진행하세요.

### Step 6: 앱 로그에서 메시지 소비 확인

```bash
# Worker가 메시지를 소비하는 로그 확인
docker logs grit-app --tail 30 | grep -E "\[알림 처리|Mock 알림"
# 기대 출력:
# [알림 처리 시작] 주문 1 - 사용자: 1, 가게: 맛있는 한식당, 금액: 16000원
# [Mock 알림] 사용자 1님, 맛있는 한식당에서 주문하신 16000원의 주문(#1)이 접수되었습니다.
# [알림] 주문 1 알림 발송 완료
```

**관찰 포인트**: 주문 API 응답과 알림 처리 로그의 시간 차이를 확인하세요. 주문 응답은 즉시 반환되고, 알림 처리는 약 500ms 후에 완료됩니다 (`sendNotification()`에서 `Thread.sleep(500)` 시뮬레이션).

### Step 7: 여러 주문을 한번에 생성하여 Queue 관찰

```bash
# 10개의 주문을 빠르게 생성
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 1}]}'
done

# 즉시 Queue 상태 확인 (처리 중인 메시지가 보일 수 있음)
docker exec grit-rabbitmq rabbitmqctl list_queues name messages consumers
# 기대 출력:
# name                          messages  consumers
# order.notification.queue      5         1
# order.notification.dlq        0         0
```

http://localhost:15672의 Queues 탭에서 실시간으로 메시지가 처리되는 것을 관찰하세요.

---

## Level 2: 변형하기 -- Worker 수 변경 + DLQ 설정

### Step 1: 대량 메시지 발행으로 부하 생성

```bash
# 100개의 주문을 빠르게 생성하여 Queue에 쌓기
for i in $(seq 1 100); do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 1}]}'
done

# Queue에 쌓인 메시지 수 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: order.notification.queue 50 (또는 이미 처리 중이라 그보다 적음)
```

### Step 2: RabbitMQ Management UI에서 처리 속도 관찰

1. http://localhost:15672 접속
2. **Queues** 탭 -> `order.notification.queue` 클릭
3. **Message rates** 그래프 확인
   - Publish rate: 초당 발행되는 메시지 수
   - Consumer ack rate: 초당 처리되는 메시지 수

**현재 상태**: Worker 1개가 순차적으로 처리 (각 알림에 500ms 소요 -> 초당 약 2개 처리)

### Step 3: Worker 수를 3개로 증가

`app/src/main/resources/application.yml` 파일 수정:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 3      # 기본 Worker 수 (기존: 1)
        max-concurrency: 5  # 최대 Worker 수
```

앱을 재시작하고 동일한 테스트 반복:

```bash
# 앱 재시작 (프로젝트 루트에서)
docker compose --profile async up -d --build app

# 다시 100개 메시지 발행
for i in $(seq 1 100); do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 1}]}'
done

# 로그에서 3개의 Worker가 동시에 처리하는 것 확인
docker logs grit-app --tail 50 | grep "알림 처리 시작"
# 기대 출력: 여러 주문 번호가 뒤섞여 병렬로 처리됨
# [알림 처리 시작] 주문 5 - ...
# [알림 처리 시작] 주문 6 - ...
# [알림 처리 시작] 주문 7 - ...  <- 3개가 동시에 처리됨
# [알림] 주문 5 알림 발송 완료
# [알림 처리 시작] 주문 8 - ...  <- 완료되자마자 다음 메시지 처리
```

**관찰 포인트**: Worker가 3개로 늘어나면 메시지 처리 속도가 약 3배 빨라집니다. RabbitMQ UI의 "Consumer ack rate"가 증가하는 것을 확인하세요.

### Step 4: Dead Letter Queue(DLQ) 설정 확인

현재 코드는 이미 DLQ가 설정되어 있습니다. `RabbitMQConfig.java`에서 확인:

```java
// 메인 큐에 DLQ 연결
@Bean
public Queue notificationQueue() {
    return QueueBuilder.durable(NOTIFICATION_QUEUE)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ)  // "order.notification.dlq"
        .build();
}

// DLQ
@Bean
public Queue notificationDlq() {
    return QueueBuilder.durable(NOTIFICATION_DLQ).build();
}
```

### Step 5: 실패 시나리오 테스트 (DLQ 동작 확인)

`NotificationService.handleOrderCreatedEvent()`에서 일부 주문을 실패 처리합니다. 테스트를 위해 코드에 예외를 추가합니다:

```java
// app/.../notification/NotificationService.java 수정
@RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
public void handleOrderCreatedEvent(Map<String, Object> message) {
    Long orderId = ((Number) message.get("orderId")).longValue();

    // 테스트: 5의 배수 주문은 실패 시뮬레이션
    if (orderId % 5 == 0) {
        throw new RuntimeException("알림 발송 실패 시뮬레이션 (주문 #" + orderId + ")");
    }

    // ... 기존 처리 코드
}
```

앱 재시작 후 테스트:

```bash
docker compose --profile async up -d --build app

# 10개 주문 생성 (주문 5번, 10번이 실패하여 DLQ로 이동 예상)
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 1}]}'
done

# DLQ에 메시지가 쌓였는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력:
# name                          messages
# order.notification.queue      0
# order.notification.dlq        2  <- 실패한 2개 메시지가 DLQ로 이동

# 앱 로그에서 실패 로그 확인
docker logs grit-app --tail 30 | grep "실패"
# 기대 출력:
# [알림 실패] 주문 5 알림 발송 중 오류 발생: 알림 발송 실패 시뮬레이션 (주문 #5)
# [알림 실패] 주문 10 알림 발송 중 오류 발생: 알림 발송 실패 시뮬레이션 (주문 #10)
```

RabbitMQ Management UI에서 `order.notification.dlq`를 클릭하면 실패한 메시지를 직접 확인하고 재처리할 수 있습니다.

---

## Level 3: 만들기 -- 멀티 워커 이벤트 처리 시스템

> **도전 과제**: 이 과제는 챕터에서 다룬 범위를 넘어선 심화 과제입니다. AI를 적극 활용하거나 공식 문서를 참고하세요.

### 요구사항

주문 완료 시 다음 3가지 작업을 각각 독립적인 Worker로 비동기 처리하는 시스템을 구현하세요:

1. **알림톡 발송** (Worker 1) - `notification.queue`
2. **이메일 발송** (Worker 2) - `email.queue`
3. **포인트 적립** (Worker 3) - `point.queue`

```
주문 완료
   ↓
order.exchange (fanout)
   ├─> notification.queue → NotificationWorker → 알림톡 발송 (200ms)
   ├─> email.queue        → EmailWorker        → 이메일 발송 (500ms)
   └─> point.queue        → PointWorker        → 포인트 적립 (100ms)
```

**핵심 조건**:
- Fanout Exchange 사용 (모든 Queue에 동일 메시지 전달)
- 각 Worker는 독립적으로 동작 (하나가 실패해도 다른 작업은 정상 처리)
- 각 Queue에 DLQ 설정
- 처리 시간 시뮬레이션: 알림(200ms), 이메일(500ms), 포인트(100ms)

### 힌트

`level3/` 폴더에 스캐폴딩 코드가 있습니다. 다음 파일을 구현하세요:

1. `OrderNotificationService.java` - Fanout Exchange를 사용한 Publisher
2. `MultiWorkerNotificationService.java` - 3개의 `@RabbitListener` 구현

Fanout Exchange 설정 예시:
```java
@Bean
public FanoutExchange fanoutExchange() {
    return new FanoutExchange("order.fanout.exchange");
}

@Bean
public Binding notificationBinding(Queue notificationQueue, FanoutExchange fanoutExchange) {
    return BindingBuilder.bind(notificationQueue).to(fanoutExchange);
}
```

### 검증

```bash
# 1. 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'

# 2. 모든 Queue에 메시지가 전달되었는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력:
# notification.queue  1
# email.queue         1
# point.queue         1

# 3. 로그에서 3개 Worker가 모두 동작하는지 확인
docker logs grit-app --tail 30
# 기대 출력 (순서는 다를 수 있음):
# [포인트 적립] 주문 #1 완료 (100ms)      <- 가장 먼저 끝남
# [알림 발송] 주문 #1 완료 (200ms)
# [이메일 발송] 주문 #1 완료 (500ms)      <- 가장 늦게 끝남

# 4. 모든 메시지가 처리되었는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: 모든 Queue 0
```

**추가 검증**: 알림 Worker에서 예외를 발생시켜도 이메일/포인트 Worker는 정상 동작하는지 확인하세요.

---

## 정리

```bash
# RabbitMQ에 쌓인 메시지 삭제 (잔여 메시지가 있을 경우)
docker exec grit-rabbitmq rabbitmqctl purge_queue order.notification.queue

# 환경 종료 (프로젝트 루트에서)
docker compose --profile async down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| **동기 vs 비동기** | 동기는 작업 완료까지 대기, 비동기는 즉시 응답 후 백그라운드 처리 |
| **RabbitMQ** | 메시지 브로커. Producer가 발행한 메시지를 Queue에 저장, Consumer가 소비 |
| **Exchange 타입** | `direct`: 라우팅 키 완전 일치, `topic`: 패턴 매칭, `fanout`: 모든 Queue에 전달 |
| **Worker Scaling** | Worker 수를 늘리면 처리 속도 선형 증가 (3개 -> 3배 빠름) |
| **DLQ** | 처리 실패한 메시지를 별도 Queue로 보내 재처리/분석 가능 |
| **Ack/Nack** | Consumer가 메시지 처리 성공(Ack) 또는 실패(Nack) 신호 전송 |

## 더 해보기 (선택)

- [ ] RabbitMQ Management UI에서 메시지 발행 속도와 소비 속도 그래프 비교
- [ ] Priority Queue 설정: 중요한 주문을 먼저 처리하도록 우선순위 부여
- [ ] Message TTL 설정: 오래된 메시지는 자동 삭제 (예: 5분 후 만료)
- [ ] Consumer Prefetch Count 조정: Worker가 한 번에 가져올 메시지 수 설정
- [ ] Delayed Message Plugin: 메시지를 일정 시간 후 전달 (예: 주문 후 10분 뒤 리뷰 요청)
