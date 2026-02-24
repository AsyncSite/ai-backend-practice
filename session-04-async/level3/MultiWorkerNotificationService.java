package com.gritmoments.backend.notification.service;

import com.gritmoments.backend.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * [Session 04 - Level 3] 멀티 워커 비동기 처리 시스템
 *
 * 주문 완료 시 다음 3가지를 비동기로 처리:
 * 1. 알림톡 발송 (Worker 1)
 * 2. 이메일 발송 (Worker 2)
 * 3. 포인트 적립 (Worker 3)
 *
 * TODO: 아래의 TODO 부분을 채워서 멀티 워커 시스템을 구현하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiWorkerNotificationService {

    // TODO 1: 필요한 의존성 주입
    // private final KakaoNotificationClient kakaoClient;
    // private final EmailService emailService;
    // private final PointService pointService;

    /**
     * Worker 1: 알림톡 발송
     *
     * RabbitMQ의 order.notification.queue에서 메시지를 소비합니다.
     * concurrency 설정으로 여러 Worker가 동시에 처리할 수 있습니다.
     */
    @RabbitListener(
        queues = "order.notification.queue",
        concurrency = "2"  // 동시에 2개의 Worker가 처리
    )
    public void sendKakaoNotification(NotificationEvent event) {
        log.info("[알림톡 Worker] 주문 {} 알림 발송 시작", event.getOrderId());

        try {
            // TODO 2: 알림톡 발송 로직 구현
            // 힌트: kakaoClient.sendMessage(event.getUserId(), "주문이 완료되었습니다.")

            // 시뮬레이션: 외부 API 호출 시간
            Thread.sleep(1000);

            log.info("[알림톡 Worker] 주문 {} 알림 발송 완료", event.getOrderId());

        } catch (Exception e) {
            log.error("[알림톡 Worker] 주문 {} 알림 발송 실패: {}", event.getOrderId(), e.getMessage());
            // TODO 3: 실패 시 재시도 또는 DLQ로 전송
            // 힌트: @RabbitListener의 errorHandler 또는 RetryTemplate 사용
        }
    }

    /**
     * Worker 2: 이메일 발송
     *
     * order.email.queue에서 메시지를 소비합니다.
     * 알림톡보다 우선순위가 낮아 별도 큐로 분리합니다.
     */
    // TODO 4: @RabbitListener 어노테이션 추가
    // @RabbitListener(queues = "order.email.queue", concurrency = "1")
    public void sendEmailNotification(NotificationEvent event) {
        log.info("[이메일 Worker] 주문 {} 이메일 발송 시작", event.getOrderId());

        try {
            // TODO 5: 이메일 발송 로직 구현
            // 힌트: emailService.send(event.getUserEmail(), "주문 완료", emailTemplate)

            // 시뮬레이션: 이메일 발송 시간 (알림톡보다 느림)
            Thread.sleep(2000);

            log.info("[이메일 Worker] 주문 {} 이메일 발송 완료", event.getOrderId());

        } catch (Exception e) {
            log.error("[이메일 Worker] 주문 {} 이메일 발송 실패: {}", event.getOrderId(), e.getMessage());
        }
    }

    /**
     * Worker 3: 포인트 적립
     *
     * order.point.queue에서 메시지를 소비합니다.
     * 트랜잭션 일관성이 중요하므로 재시도 정책을 엄격하게 설정합니다.
     */
    // TODO 6: @RabbitListener 어노테이션 추가
    // @RabbitListener(queues = "order.point.queue", concurrency = "1")
    public void accumulatePoints(NotificationEvent event) {
        log.info("[포인트 Worker] 주문 {} 포인트 적립 시작", event.getOrderId());

        try {
            // TODO 7: 포인트 적립 로직 구현
            // 힌트: pointService.accumulate(event.getUserId(), event.getOrderAmount() * 0.01)

            // 중복 적립 방지를 위한 멱등키 검증
            // TODO 8: 이미 적립된 주문인지 확인
            // 힌트: pointService.isAlreadyAccumulated(event.getOrderId())

            // 시뮬레이션: DB 업데이트 시간
            Thread.sleep(500);

            log.info("[포인트 Worker] 주문 {} 포인트 적립 완료", event.getOrderId());

        } catch (Exception e) {
            log.error("[포인트 Worker] 주문 {} 포인트 적립 실패: {}", event.getOrderId(), e.getMessage());
            // TODO 9: 포인트 적립 실패 시 보상 트랜잭션 또는 알림
            throw e;  // 재시도를 위해 예외를 다시 던짐
        }
    }

    /**
     * Dead Letter Queue (DLQ) 처리
     *
     * 재시도 횟수를 초과한 메시지를 처리합니다.
     * 관리자에게 알림을 보내거나 별도 DB에 저장합니다.
     */
    // TODO 10: DLQ 리스너 구현
    // @RabbitListener(queues = "order.notification.dlq")
    public void handleFailedNotifications(NotificationEvent event) {
        log.error("[DLQ] 처리 실패한 알림: 주문 {}", event.getOrderId());

        // TODO 11: 실패한 메시지 처리
        // 힌트:
        //   1. 실패 로그를 DB에 저장
        //   2. 관리자에게 Slack 알림
        //   3. 수동 처리를 위한 대시보드에 표시
    }
}


// ============================================
// 💡 RabbitMQ 설정 (application.yml)
// ============================================
//
// spring:
//   rabbitmq:
//     host: localhost
//     port: 5672
//     username: guest
//     password: guest
//     listener:
//       simple:
//         concurrency: 1       # 기본 Worker 수
//         max-concurrency: 4   # 최대 Worker 수
//         prefetch: 1          # Worker당 한 번에 가져올 메시지 수
//         retry:
//           enabled: true
//           initial-interval: 1000ms  # 첫 재시도 간격
//           max-attempts: 3           # 최대 재시도 횟수
//           multiplier: 2.0           # 재시도 간격 증가율


// ============================================
// 💡 큐 설정 (RabbitMQConfig.java)
// ============================================
//
// @Configuration
// public class RabbitMQConfig {
//
//     @Bean
//     public Queue notificationQueue() {
//         return QueueBuilder.durable("order.notification.queue")
//             .withArgument("x-dead-letter-exchange", "dlx.exchange")
//             .withArgument("x-dead-letter-routing-key", "dlq.notification")
//             .build();
//     }
//
//     @Bean
//     public Queue emailQueue() {
//         return new Queue("order.email.queue", true);
//     }
//
//     @Bean
//     public Queue pointQueue() {
//         return new Queue("order.point.queue", true);
//     }
//
//     @Bean
//     public Queue deadLetterQueue() {
//         return new Queue("order.notification.dlq", true);
//     }
//
//     @Bean
//     public DirectExchange deadLetterExchange() {
//         return new DirectExchange("dlx.exchange");
//     }
//
//     @Bean
//     public Binding dlqBinding() {
//         return BindingBuilder
//             .bind(deadLetterQueue())
//             .to(deadLetterExchange())
//             .with("dlq.notification");
//     }
// }


// ============================================
// 💡 메시지 발행 (OrderService.java)
// ============================================
//
// @Service
// @RequiredArgsConstructor
// public class OrderService {
//
//     private final RabbitTemplate rabbitTemplate;
//
//     public void createOrder(OrderRequest request) {
//         // 주문 생성 로직
//         Order order = orderRepository.save(newOrder);
//
//         // 이벤트 생성
//         NotificationEvent event = NotificationEvent.builder()
//             .orderId(order.getId())
//             .userId(order.getUserId())
//             .orderAmount(order.getTotalAmount())
//             .build();
//
//         // 3개의 큐에 메시지 발행
//         rabbitTemplate.convertAndSend("order.notification.queue", event);
//         rabbitTemplate.convertAndSend("order.email.queue", event);
//         rabbitTemplate.convertAndSend("order.point.queue", event);
//     }
// }


// ============================================
// 💡 테스트 방법
// ============================================
//
// 1. 주문 생성 API 호출:
//    curl -X POST http://localhost:8080/api/orders \
//      -H "Content-Type: application/json" \
//      -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'
//
// 2. RabbitMQ Management UI에서 확인:
//    - http://localhost:15672
//    - 각 큐의 메시지 수와 처리율 확인
//
// 3. 로그에서 Worker 동작 확인:
//    docker logs grit-app --tail 50 | grep "Worker"
//
// 4. 부하 테스트:
//    for i in {1..100}; do
//      curl -X POST http://localhost:8080/api/orders \
//        -H "Content-Type: application/json" \
//        -d '{"userId": 1, "restaurantId": 1}' &
//    done
//    wait


// ============================================
// 💡 운영 고려사항
// ============================================
//
// 1. Worker 수 설정:
//    - CPU 집약적: CPU 코어 수만큼
//    - I/O 대기: CPU 코어 수의 2~4배
//
// 2. Prefetch 설정:
//    - 낮을수록: 공평한 분배, 느린 Worker가 영향
//    - 높을수록: 빠른 처리, 불균형 가능
//
// 3. 재시도 전략:
//    - 멱등성 보장 (중복 처리 방지)
//    - 지수 백오프 (외부 시스템 복구 시간 확보)
//    - 최대 재시도 횟수 제한 (무한 루프 방지)
//
// 4. DLQ 모니터링:
//    - 정기적으로 DLQ 크기 확인
//    - 실패 패턴 분석 (특정 케이스만 실패?)
//    - 수동 재처리 프로세스 마련
