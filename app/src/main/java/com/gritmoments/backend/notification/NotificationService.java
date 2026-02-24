package com.gritmoments.backend.notification;

import com.gritmoments.backend.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 알림 서비스 (세션 04: 비동기 처리 - RabbitMQ)
 *
 * RabbitMQ를 사용하여 비동기 알림을 처리합니다.
 * - 주문 생성 시 메시지를 큐에 발행 (Publisher)
 * - 큐에서 메시지를 소비하여 알림 발송 (Consumer)
 * - 처리 실패 시 DLQ(Dead Letter Queue)로 이동
 *
 * 비동기 처리 장점:
 * - 주문 API 응답 속도 향상 (알림 발송 대기 불필요)
 * - 알림 실패가 주문 트랜잭션에 영향을 주지 않음
 * - 메시지 큐를 통한 안정적인 전달 보장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 주문 알림 발행 (세션 04: RabbitMQ Producer)
     *
     * 주문이 생성되면 RabbitMQ에 메시지를 발행합니다.
     * 메시지는 order.exchange로 전송되고, order.created 라우팅 키로
     * order.notification.queue에 저장됩니다.
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param restaurantName 가게 이름
     * @param totalAmount 총 금액
     */
    public void publishOrderCreatedEvent(Long orderId, Long userId, String restaurantName, Integer totalAmount) {
        log.info("[알림 발행] 주문 {} 생성 이벤트를 큐에 발행합니다.", orderId);

        // 메시지 페이로드 구성
        Map<String, Object> message = Map.of(
                "orderId", orderId,
                "userId", userId,
                "restaurantName", restaurantName,
                "totalAmount", totalAmount,
                "timestamp", System.currentTimeMillis()
        );

        // RabbitMQ에 메시지 발행
        // Exchange: RabbitMQConfig.ORDER_EXCHANGE
        // Routing Key: RabbitMQConfig.ORDER_CREATED_KEY
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CREATED_KEY,
                message
        );

        log.info("[알림 발행 완료] 주문 {} 이벤트가 큐에 저장되었습니다.", orderId);
    }

    /**
     * 주문 알림 소비 (세션 04: RabbitMQ Consumer)
     *
     * order.notification.queue에서 메시지를 소비하여 실제 알림을 발송합니다.
     * @RabbitListener가 자동으로 큐를 모니터링하고 메시지를 처리합니다.
     *
     * 처리 흐름:
     * 1. 큐에서 메시지 수신
     * 2. 알림 발송 로직 실행 (이메일, SMS, 푸시 등)
     * 3. 성공 시 메시지 ACK (큐에서 제거)
     * 4. 실패 시 메시지 NACK (재시도 또는 DLQ로 이동)
     *
     * @param message 주문 정보가 담긴 메시지
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleOrderCreatedEvent(Map<String, Object> message) {
        Long orderId = ((Number) message.get("orderId")).longValue();
        Long userId = ((Number) message.get("userId")).longValue();
        String restaurantName = (String) message.get("restaurantName");
        Integer totalAmount = ((Number) message.get("totalAmount")).intValue();

        log.info("[알림 처리 시작] 주문 {} - 사용자: {}, 가게: {}, 금액: {}원",
                orderId, userId, restaurantName, totalAmount);

        try {
            // 실제 알림 발송 로직 (이메일, SMS, 푸시 등)
            sendNotification(orderId, userId, restaurantName, totalAmount);

            log.info("[알림] 주문 {} 알림 발송 완료", orderId);

        } catch (Exception e) {
            log.error("[알림 실패] 주문 {} 알림 발송 중 오류 발생: {}", orderId, e.getMessage(), e);
            // 예외를 던지면 메시지가 재처리되거나 DLQ로 이동
            throw new RuntimeException("알림 발송 실패", e);
        }
    }

    /**
     * 실제 알림 발송 로직 (세션 04: 외부 서비스 연동 시뮬레이션)
     *
     * 실제 환경에서는 여기서 이메일 서비스, SMS API, 푸시 알림 서비스 등을 호출합니다.
     * 예: SendGrid, Twilio, FCM, AWS SES 등
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param restaurantName 가게 이름
     * @param totalAmount 총 금액
     */
    private void sendNotification(Long orderId, Long userId, String restaurantName, Integer totalAmount) {
        // Mock 알림 발송
        // TODO: 실제 이메일/푸시 알림 서비스 연동
        log.info("[Mock 알림] 사용자 {}님, {}에서 주문하신 {}원의 주문(#{})이 접수되었습니다.",
                userId, restaurantName, totalAmount, orderId);

        // 알림 발송 시뮬레이션 (0.5초 대기)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
