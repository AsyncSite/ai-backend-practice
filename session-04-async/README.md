# [Session 04] 비동기 처리 -- RabbitMQ로 알림 시스템 구축하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- 동기/비동기 방식의 응답 시간 차이를 직접 측정할 수 있다
- RabbitMQ에 메시지를 발행(Publish)하고 소비(Consume)하는 코드를 작성할 수 있다
- Worker 수에 따른 처리 속도 변화를 관찰할 수 있다
- Dead Letter Queue(DLQ)로 실패한 메시지를 관리할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **중요**: `exercises/` 디렉토리(상위 폴더)에서 Docker Compose 실행
  ```bash
  cd exercises/
  docker compose --profile async up -d
  ```
- 실행되는 컨테이너: `grit-mysql`, `grit-rabbitmq`, `grit-app`
- RabbitMQ Management UI: http://localhost:15672 (guest/guest)

## 핵심 개념

```
동기 처리 (Synchronous):
  주문 요청 -> DB 저장 (50ms) -> 알림 발송 (200ms) -> 응답 (총 250ms)
  사용자는 알림 발송이 끝날 때까지 대기

비동기 처리 (Asynchronous):
  주문 요청 -> DB 저장 (50ms) -> 메시지 발행 (1ms) -> 응답 (총 51ms)
                                      ↓
                            RabbitMQ Queue
                                      ↓
                            Worker가 별도로 알림 발송 (200ms)
  사용자는 즉시 응답 받고, 알림은 백그라운드에서 처리
```

---

## Level 1: 따라하기 -- 메시지 발행/소비 기본 동작

### Step 1: 환경 확인

```bash
# 앱 서버 상태 확인
curl http://localhost:8080/actuator/health
# 기대 출력: {"status":"UP"}

# RabbitMQ 컨테이너 상태 확인
docker ps | grep rabbitmq
# 기대 출력: rabbitmq 컨테이너가 Up 상태

# RabbitMQ 연결 확인
docker exec grit-rabbitmq rabbitmqctl status
# 기대 출력: RabbitMQ 상태 정보 출력
```

### Step 2: RabbitMQ 관리 UI 확인

브라우저에서 http://localhost:15672 접속 (ID: guest, PW: guest)

1. **Queues 탭** 클릭
   - `order.notification.queue` 확인
   - **Ready**: 처리 대기 중인 메시지 수 (큐에 쌓여있음)
   - **Unacked**: Worker가 처리 중인 메시지 수 (아직 ACK 안 됨)
   - **Total**: 전체 메시지 수 (Ready + Unacked)

2. **Exchanges 탭** 클릭
   - `order.exchange` 확인
   - Type: `topic` (라우팅 키 패턴 매칭)

### Step 3: 동기 방식으로 주문 생성 (비교 기준)

```bash
# 동기 방식 주문 생성 (알림을 즉시 발송하여 응답 느림)
curl -X POST http://localhost:8080/api/orders/sync \
  -H "Content-Type: application/json" \
  -w "\n응답시간: %{time_total}s\n" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'

# 5번 반복하여 평균 응답 시간 측정
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/orders/sync \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -w "동기 요청 $i: %{time_total}s\n" \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
done
# 기대 출력: 각 요청마다 0.2~0.3초 소요 (알림 발송 시간 포함)
```

### Step 4: 비동기 방식으로 주문 생성 (메시지 발행)

```bash
# 비동기 방식 주문 생성 (메시지만 발행하고 즉시 응답)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -w "\n응답시간: %{time_total}s\n" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'

# 5번 반복하여 평균 응답 시간 측정
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -w "비동기 요청 $i: %{time_total}s\n" \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
done
# 기대 출력: 각 요청마다 0.05초 내외 (메시지 발행만 하므로 빠름)
```

**관찰 포인트**: 비동기 방식이 동기 방식보다 4~5배 빠릅니다. 사용자는 즉시 응답을 받고, 알림은 백그라운드에서 처리됩니다.

### Step 5: RabbitMQ에서 메시지 확인

```bash
# RabbitMQ Management UI에서 Queues 탭으로 이동
# order.notification.queue 클릭 -> "Get messages" 섹션에서 메시지 내용 확인
```

또는 CLI로 확인:

```bash
# Queue에 쌓인 메시지 수 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: order.notification.queue 5 (5개 메시지 대기 중)
```

### Step 6: 앱 로그에서 메시지 소비 확인

```bash
# Worker가 메시지를 소비하는 로그 확인
docker logs grit-app --tail 30 | grep "알림"
# 기대 출력:
# [알림 발송] 주문 #1 - 사용자 1에게 알림 발송 완료
# [알림 발송] 주문 #2 - 사용자 1에게 알림 발송 완료
# ...
```

### Step 7: 메시지 처리 후 Queue 상태 재확인

```bash
# 모든 메시지가 처리되었는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: order.notification.queue 0 (모든 메시지 처리 완료)
```

---

## Level 2: 변형하기 -- Worker 수 변경 + DLQ 설정

### Step 1: 대량 메시지 발행으로 부하 생성

```bash
# 100개의 주문을 빠르게 생성하여 Queue에 쌓기
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
done

# Queue에 쌓인 메시지 수 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: order.notification.queue 100 (또는 처리 중이라 그보다 적음)
```

### Step 2: RabbitMQ Management UI에서 처리 속도 관찰

1. http://localhost:15672 접속
2. **Queues** 탭 → `order.notification.queue` 클릭
3. **Message rates** 그래프 확인
   - Publish rate: 초당 발행되는 메시지 수
   - Consumer ack rate: 초당 처리되는 메시지 수

**현재 상태**: Worker 1개가 순차적으로 처리 (처리 속도 느림)

### Step 3: Worker 수를 3개로 증가

`app/src/main/resources/application.yml` 파일 수정:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 3  # 기본 Worker 수 (기존: 1)
        max-concurrency: 5  # 최대 Worker 수
```

앱을 재시작하고 동일한 테스트 반복:

```bash
# 앱 재시작
docker compose up -d --build app

# 다시 100개 메시지 발행
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
done

# 로그에서 3개의 Worker가 동시에 처리하는 것 확인
docker logs grit-app --tail 50 | grep "알림"
# 기대 출력: 여러 Worker가 병렬로 처리하는 로그 (처리 속도 3배 향상)
```

**관찰 포인트**: Worker가 3개로 늘어나면 메시지 처리 속도가 약 3배 빨라집니다. RabbitMQ UI의 "Consumer ack rate"가 증가합니다.

### Step 4: Dead Letter Queue(DLQ) 설정

DLQ는 처리 실패한 메시지를 별도 Queue로 보내 나중에 재처리하거나 분석할 수 있게 합니다.

`app/src/main/java/com/gritmoments/backend/config/RabbitMQConfig.java` 파일 수정:

```java
@Bean
public Queue notificationQueue() {
    return QueueBuilder.durable("order.notification.queue")
        .withArgument("x-dead-letter-exchange", "dlx.exchange")  // DLQ Exchange
        .withArgument("x-dead-letter-routing-key", "dlq.order.notification")  // DLQ 라우팅 키
        .build();
}

@Bean
public Queue deadLetterQueue() {
    return QueueBuilder.durable("dlq.order.notification.queue").build();
}

@Bean
public DirectExchange deadLetterExchange() {
    return new DirectExchange("dlx.exchange");
}

@Bean
public Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue())
        .to(deadLetterExchange())
        .with("dlq.order.notification");
}
```

### Step 5: 실패 시나리오 테스트

Consumer에서 예외를 발생시켜 DLQ로 메시지가 이동하는지 확인:

```java
// app/src/main/java/com/gritmoments/backend/order/OrderNotificationConsumer.java
@RabbitListener(queues = "order.notification.queue")
public void handleOrderNotification(OrderEvent event) {
    if (event.getOrderId() % 5 == 0) {  // 5의 배수 주문은 실패 시뮬레이션
        throw new RuntimeException("알림 발송 실패 시뮬레이션");
    }
    log.info("[알림 발송] 주문 #{} - 사용자 {}에게 알림 발송 완료",
             event.getOrderId(), event.getUserId());
}
```

앱 재시작 후 테스트:

```bash
docker compose up -d --build app

# 10개 주문 생성 (일부는 실패하여 DLQ로 이동)
for i in {1..10}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -o /dev/null \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
done

# DLQ에 메시지가 쌓였는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: dlq.order.notification.queue 2 (주문 5, 10이 실패하여 DLQ로 이동)
```

RabbitMQ Management UI에서 `dlq.order.notification.queue`를 확인하면 실패한 메시지를 볼 수 있습니다.

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
   ├─> notification.queue → NotificationWorker → 알림톡 발송
   ├─> email.queue → EmailWorker → 이메일 발송
   └─> point.queue → PointWorker → 포인트 적립
```

**핵심 조건**:
- Fanout Exchange 사용 (모든 Queue에 동일 메시지 전달)
- 각 Worker는 독립적으로 동작 (하나가 실패해도 다른 작업은 정상 처리)
- 각 Queue에 DLQ 설정
- 처리 시간 시뮬레이션: 알림(200ms), 이메일(500ms), 포인트(100ms)

### 힌트

`level3/` 폴더에 스캐폴딩 코드가 있습니다. 다음 파일을 구현하세요:

1. `RabbitMQConfig.java` - Fanout Exchange, 3개 Queue, Binding 설정
2. `NotificationWorker.java` - `@RabbitListener(queues = "notification.queue")`
3. `EmailWorker.java` - `@RabbitListener(queues = "email.queue")`
4. `PointWorker.java` - `@RabbitListener(queues = "point.queue")`

### 검증

```bash
# 1. 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'

# 2. 모든 Queue에 메시지가 전달되었는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력:
# notification.queue 1
# email.queue 1
# point.queue 1

# 3. 로그에서 3개 Worker가 모두 동작하는지 확인
docker logs grit-app --tail 30
# 기대 출력:
# [알림 발송] 주문 #1 완료
# [이메일 발송] 주문 #1 완료
# [포인트 적립] 주문 #1 완료

# 4. 모든 메시지가 처리되었는지 확인
docker exec grit-rabbitmq rabbitmqctl list_queues name messages
# 기대 출력: 모든 Queue 0
```

**추가 검증**: 한 Worker에서 예외를 발생시켜도 다른 Worker는 정상 동작하는지 확인하세요.

---

## 정리

```bash
# RabbitMQ에 쌓인 모든 메시지 삭제
docker exec grit-rabbitmq rabbitmqctl purge_queue order.notification.queue

# 환경 종료
docker compose --profile async down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| **동기 vs 비동기** | 동기는 작업 완료까지 대기, 비동기는 즉시 응답 후 백그라운드 처리 |
| **RabbitMQ** | 메시지 브로커. Producer가 발행한 메시지를 Queue에 저장, Consumer가 소비 |
| **Exchange 타입** | `direct`: 라우팅 키 완전 일치, `topic`: 패턴 매칭, `fanout`: 모든 Queue에 전달 |
| **Worker Scaling** | Worker 수를 늘리면 처리 속도 선형 증가 (3개 → 3배 빠름) |
| **DLQ** | 처리 실패한 메시지를 별도 Queue로 보내 재처리/분석 가능 |
| **Ack/Nack** | Consumer가 메시지 처리 성공(Ack) 또는 실패(Nack) 신호 전송 |

## 더 해보기 (선택)

- [ ] RabbitMQ Management UI에서 메시지 발행 속도와 소비 속도 그래프 비교
- [ ] Priority Queue 설정: 중요한 주문을 먼저 처리하도록 우선순위 부여
- [ ] Message TTL 설정: 오래된 메시지는 자동 삭제 (예: 5분 후 만료)
- [ ] Consumer Prefetch Count 조정: Worker가 한 번에 가져올 메시지 수 설정
- [ ] RabbitMQ 클러스터링: 여러 노드로 RabbitMQ를 구성하여 고가용성 확보
- [ ] Delayed Message Plugin: 메시지를 일정 시간 후 전달 (예: 주문 후 10분 뒤 리뷰 요청)
