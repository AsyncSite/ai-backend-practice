package com.gritmoments.backend.order.service;

import com.gritmoments.backend.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * [Session 04 - Level 3] RabbitMQ 기반 비동기 주문 알림 서비스
 *
 * 동기 처리의 문제점:
 *   주문 생성 -> 결제 -> 알림(이메일, 푸시, SMS) -> 응답
 *   알림 처리에 5초 걸리면? 사용자가 5초를 기다려야 합니다.
 *
 * 비동기 처리의 장점:
 *   주문 생성 -> 결제 -> [큐에 메시지 발행] -> 즉시 응답 (빠름!)
 *                         └-> [비동기] 알림 서비스가 메시지를 소비하여 처리
 *
 * 핵심 학습 포인트:
 *   1. RabbitTemplate으로 메시지 발행 (Producer)
 *   2. @RabbitListener로 메시지 소비 (Consumer)
 *   3. JSON 직렬화/역직렬화
 *   4. 메시지 처리 실패 시 DLQ(Dead Letter Queue) 활용
 *
 * TODO: 아래의 TODO 부분을 채워서 비동기 알림 시스템을 완성하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final RabbitTemplate rabbitTemplate;

    // =========================================================================
    // 1단계: 메시지 발행 (Producer)
    // =========================================================================

    /**
     * 주문 완료 이벤트를 큐에 발행
     *
     * 이 메서드는 주문 완료 후 호출됩니다.
     * 메시지를 큐에 넣고 즉시 리턴하므로 빠릅니다.
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 주문 총액
     */
    public void publishOrderCreatedEvent(Long orderId, Long userId, Integer totalAmount) {
        log.info("[메시지 발행] 주문 알림 이벤트. 주문 ID: {}", orderId);

        // TODO 1: 메시지 본문(payload)을 Map으로 생성하세요
        //
        // 포함할 필드:
        //   - "orderId": orderId
        //   - "userId": userId
        //   - "totalAmount": totalAmount
        //   - "eventType": "ORDER_CREATED"
        //   - "timestamp": LocalDateTime.now().toString()
        //
        // 힌트:
        //   Map<String, Object> message = Map.of(
        //       "orderId", orderId,
        //       "userId", userId,
        //       "totalAmount", totalAmount,
        //       "eventType", "ORDER_CREATED",
        //       "timestamp", LocalDateTime.now().toString()
        //   );

        // TODO 2: RabbitTemplate을 사용하여 메시지를 발행하세요
        //
        // 파라미터:
        //   - exchange: RabbitMQConfig.ORDER_EXCHANGE
        //   - routingKey: RabbitMQConfig.ORDER_CREATED_KEY
        //   - message: 위에서 생성한 Map 객체
        //
        // 힌트:
        //   rabbitTemplate.convertAndSend(
        //       RabbitMQConfig.ORDER_EXCHANGE,
        //       RabbitMQConfig.ORDER_CREATED_KEY,
        //       message
        //   );

        log.info("[메시지 발행 완료] 주문 ID: {} -> 큐로 전송됨", orderId);
    }

    // =========================================================================
    // 2단계: 메시지 소비 (Consumer)
    // =========================================================================

    /**
     * 주문 알림 메시지를 큐에서 수신하여 처리
     *
     * @RabbitListener: 지정된 큐에서 메시지가 도착하면 자동으로 이 메서드를 호출합니다.
     * - 메시지가 없으면 대기 (블로킹하지 않음, 이벤트 드리븐)
     * - 메시지가 도착하면 JSON -> Map으로 자동 역직렬화
     * - 처리 완료 시 자동 ACK (메시지 큐에서 제거)
     * - 예외 발생 시 NACK -> DLQ로 이동 (RabbitMQConfig 설정에 따라)
     *
     * @param message 수신된 메시지 (JSON -> Map 자동 변환)
     */
    // TODO 3: @RabbitListener 어노테이션을 추가하세요
    //
    // 설정할 값:
    //   - queues: RabbitMQConfig.NOTIFICATION_QUEUE
    //
    // 힌트:
    //   @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleOrderNotification(Map<String, Object> message) {
        log.info("[메시지 수신] 주문 알림 처리 시작: {}", message);

        // TODO 4: 메시지에서 필요한 정보를 추출하세요
        //
        // 추출할 필드:
        //   - Long orderId = ((Number) message.get("orderId")).longValue();
        //   - Long userId = ((Number) message.get("userId")).longValue();
        //   - Integer totalAmount = ((Number) message.get("totalAmount")).intValue();
        //   - String eventType = (String) message.get("eventType");

        // TODO 5: 알림 처리 로직을 구현하세요
        //
        // 실제 서비스에서는 여기서 다음과 같은 작업을 수행합니다:
        //   1. 이메일 알림 발송
        //   2. 푸시 알림 발송
        //   3. SMS 알림 발송
        //   4. 가게 주인에게 신규 주문 알림
        //
        // 이 실습에서는 로그로 대체합니다:
        //   log.info("[이메일 알림] 사용자 {}에게 주문 확인 메일 발송. 주문 ID: {}, 금액: {}원",
        //       userId, orderId, totalAmount);
        //   log.info("[푸시 알림] 사용자 {}에게 주문 접수 알림 발송", userId);
        //   log.info("[가게 알림] 신규 주문 접수. 주문 ID: {}", orderId);

        // 비동기 처리의 핵심: 여기서 시간이 오래 걸려도 사용자 응답에 영향 없음
        try {
            Thread.sleep(2000); // 알림 발송 시뮬레이션 (2초 소요)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[메시지 처리 완료] 주문 알림 처리 완료");
    }

    // =========================================================================
    // 3단계: 에러 처리 (심화)
    // =========================================================================

    /**
     * DLQ(Dead Letter Queue) 메시지 처리
     *
     * 메인 큐에서 처리 실패한 메시지가 DLQ로 이동합니다.
     * DLQ 메시지를 모니터링하여 장애를 감지하고 수동 처리합니다.
     */
    // TODO 6: DLQ 리스너를 추가하세요
    //
    // 힌트:
    //   @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_DLQ)
    public void handleDeadLetterMessage(Map<String, Object> message) {
        // TODO 7: DLQ 메시지 처리 로직을 구현하세요
        //
        // 처리 방법:
        //   1. 에러 로그 기록 (운영팀 알림용)
        //   2. 실패한 메시지 내용을 DB에 저장하거나 별도 로그 파일에 기록
        //   3. 슬랙/이메일로 운영팀에 알림
        //
        // 힌트:
        //   log.error("[DLQ] 처리 실패한 메시지 감지. 수동 확인 필요: {}", message);
        //   // 실제로는 여기서 DB에 실패 이력을 저장하거나 운영 알림을 보냅니다.
    }
}
