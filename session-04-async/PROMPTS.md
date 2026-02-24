# [Session 04] AI 활용 프롬프트 -- 비동기 처리

## 1단계: 이해

```
RabbitMQ의 Exchange, Queue, Binding이 뭔지 설명해줘.
Direct, Topic, Fanout Exchange의 차이를 실제 사용 사례와 함께 알려줘.
```

```
RabbitMQ의 ack/nack 메커니즘을 설명해줘.
메시지 처리가 실패했을 때 재시도하거나 DLQ로 보내는 흐름을 코드로 보여줘.
```

## 2단계: 적용

```
Spring AMQP로 DLQ 설정이 포함된 RabbitMQ Listener를 만들어줘.
요구사항:
- 주문 알림 메시지를 소비
- 실패 시 3번 재시도 후 DLQ로 이동
- 메시지는 JSON 형식
```

## 3단계: 검증

```
메시지 순서가 보장되지 않을 때 문제가 되는 시나리오는?
주문 생성 -> 결제 완료 -> 배달 시작 순서가 뒤바뀌면 어떻게 돼?
```

```
[AI가 틀릴 수 있는 포인트]
RabbitMQ와 Kafka의 선택은 서비스 요구사항에 따라 다릅니다.
AI가 무조건 Kafka를 추천할 수 있지만, 소규모 서비스에서는 RabbitMQ가 더 적합합니다.
```
