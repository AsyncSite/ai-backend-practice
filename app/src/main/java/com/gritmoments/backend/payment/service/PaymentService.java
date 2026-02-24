package com.gritmoments.backend.payment.service;

import com.gritmoments.backend.common.exception.BusinessException;
import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import com.gritmoments.backend.order.entity.Order;
import com.gritmoments.backend.order.repository.OrderRepository;
import com.gritmoments.backend.payment.entity.Payment;
import com.gritmoments.backend.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 결제 서비스 (세션 03: 외부 API 연동)
 *
 * 외부 PG(Payment Gateway)와 연동하여 결제를 처리합니다.
 * - 멱등키(Idempotency Key): 동일 요청의 중복 결제 방지
 * - Resilience4j: 서킷 브레이커와 재시도로 장애 대응
 * - RestTemplate: 외부 PG API 호출 (타임아웃 설정 포함)
 * - 트랜잭션 관리: 결제 실패 시 롤백
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    @Value("${mock-pg.url}")
    private String mockPgUrl;

    /**
     * 결제 요청 (세션 03: 멱등성 보장 + 외부 PG 연동)
     *
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param idempotencyKey 멱등키 (같은 키로 재요청 시 기존 결제 반환)
     * @return 결제 정보
     */
    @Transactional
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    public Payment requestPayment(Long orderId, Integer amount, String idempotencyKey) {
        log.info("[결제 요청 시작] 주문 ID: {}, 금액: {}, 멱등키: {}", orderId, amount, idempotencyKey);

        // 1. 멱등성 체크: 같은 멱등키로 이미 결제가 진행된 경우 기존 결제 반환
        Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existingPayment != null) {
            log.warn("[중복 결제 감지] 멱등키: {}, 기존 결제 ID: {}", idempotencyKey, existingPayment.getId());
            return existingPayment;
        }

        // 2. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 3. 주문 상태 검증
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new BusinessException("결제 가능한 주문이 아닙니다. 현재 상태: " + order.getStatus());
        }

        // 4. 결제 엔티티 생성 (PENDING 상태)
        Payment payment = Payment.builder()
                .order(order)
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                .build();
        payment = paymentRepository.save(payment);
        log.info("[결제 엔티티 생성] Payment ID: {}, 상태: PENDING", payment.getId());

        // 5. 외부 PG(Payment Gateway) 호출 (세션 03: 외부 API 연동)
        try {
            String pgTransactionId = callExternalPG(orderId, amount, idempotencyKey);
            payment.markAsSuccess(pgTransactionId);
            order.markAsPaid(); // 주문 상태를 PAID로 변경
            log.info("[결제 성공] Payment ID: {}, PG 트랜잭션 ID: {}", payment.getId(), pgTransactionId);
        } catch (Exception e) {
            payment.markAsFailed();
            log.error("[결제 실패] Payment ID: {}, 사유: {}", payment.getId(), e.getMessage());
            throw new BusinessException("결제 처리 중 오류가 발생했습니다: " + e.getMessage());
        }

        return payment;
    }

    /**
     * 결제 상태 조회
     */
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    /**
     * 주문 ID로 결제 조회
     */
    public Payment findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
    }

    /**
     * 외부 PG(Payment Gateway) 호출 (세션 03: RestTemplate 사용)
     *
     * RestTemplate으로 Mock PG API를 호출합니다.
     * - URL: application.yml의 mock-pg.url 설정값 사용
     * - 타임아웃: RestTemplate Bean에서 설정 (연결 타임아웃, 읽기 타임아웃)
     * - 재시도: @Retry 어노테이션으로 자동 재시도
     * - 서킷 브레이커: @CircuitBreaker로 장애 전파 차단
     *
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param idempotencyKey 멱등키
     * @return PG사에서 발급한 트랜잭션 ID
     */
    private String callExternalPG(Long orderId, Integer amount, String idempotencyKey) {
        log.info("[PG 요청 시작] URL: {}/api/payments, 주문 ID: {}, 금액: {}", mockPgUrl, orderId, amount);

        // PG 요청 데이터 생성
        Map<String, Object> request = Map.of(
                "orderId", orderId,
                "amount", amount,
                "idempotencyKey", idempotencyKey
        );

        // RestTemplate으로 POST 요청 (타임아웃 설정은 Bean에서 처리)
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                mockPgUrl + "/api/payments",
                request,
                Map.class
        );

        // PG 응답 처리
        if (response != null && "SUCCESS".equals(response.get("status"))) {
            String pgTransactionId = (String) response.get("transactionId");
            log.info("[PG 응답 성공] 트랜잭션 ID: {}", pgTransactionId);
            return pgTransactionId;
        } else {
            log.error("[PG 응답 실패] Response: {}", response);
            throw new RuntimeException("PG 결제 승인 실패");
        }
    }

    /**
     * 서킷 브레이커 Fallback 메서드 (세션 03: 장애 대응)
     *
     * PG 시스템 장애 시 호출되는 대체 로직입니다.
     * - 서킷 브레이커가 OPEN 상태일 때 자동 호출
     * - 결제를 FAILED 상태로 저장하여 나중에 수동 처리 가능
     */
    private Payment paymentFallback(Long orderId, Integer amount, String idempotencyKey, Exception e) {
        log.error("[서킷 브레이커 동작] PG 시스템 장애 - 주문 ID: {}, 사유: {}", orderId, e.getMessage());

        // 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 실패 상태로 결제 엔티티 저장
        Payment payment = Payment.builder()
                .order(order)
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                .build();
        payment.markAsFailed();

        return paymentRepository.save(payment);
    }
}
