package com.gritmoments.backend.payment.service;

import com.gritmoments.backend.order.entity.Order;
import com.gritmoments.backend.payment.entity.Payment;
import com.gritmoments.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * [Session 03 - Level 3] 외부 PG 연동 + 재시도 패턴 직접 구현
 *
 * 외부 API 호출 시 발생할 수 있는 문제들을 처리합니다:
 * 1. 타임아웃 설정 - 외부 서버가 응답하지 않을 때 무한 대기 방지
 * 2. 재시도(Retry) - 일시적 장애 시 자동으로 다시 시도
 * 3. 폴백(Fallback) - 모든 재시도 실패 시 대안 처리
 *
 * TODO: 아래의 TODO 부분을 채워서 안전한 외부 API 연동을 완성하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceWithRetry {

    private final PaymentRepository paymentRepository;

    @Value("${mock.pg.url:http://localhost:9000}")
    private String pgBaseUrl;

    // =========================================================================
    // 1단계: RestTemplate 타임아웃 설정
    // =========================================================================

    /**
     * 타임아웃이 설정된 RestTemplate 생성
     *
     * 외부 API 호출 시 반드시 타임아웃을 설정해야 합니다.
     * 타임아웃이 없으면 외부 서버 장애 시 우리 서버도 함께 멈춥니다.
     */
    private RestTemplate createRestTemplate() {
        // TODO 1: RestTemplateBuilder를 사용하여 타임아웃이 설정된 RestTemplate을 생성하세요
        //
        // 설정할 값:
        //   - connectTimeout: 3초 (서버 연결까지 최대 대기 시간)
        //   - readTimeout: 5초 (응답 수신까지 최대 대기 시간)
        //
        // 힌트:
        //   return new RestTemplateBuilder()
        //       .setConnectTimeout(Duration.ofSeconds(???))
        //       .setReadTimeout(Duration.ofSeconds(???))
        //       .build();

        return new RestTemplate(); // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }

    // =========================================================================
    // 2단계: @Retryable로 자동 재시도
    // =========================================================================

    /**
     * PG사에 결제 요청 (재시도 적용)
     *
     * @Retryable 어노테이션으로 일시적 장애 시 자동으로 재시도합니다.
     * - 네트워크 오류, 타임아웃 등의 일시적 장애에 효과적
     * - 지수 백오프(Exponential Backoff)로 서버 부하 방지
     *
     * @param order 결제할 주문
     * @return 결제 결과
     */
    // TODO 2: @Retryable 어노테이션을 추가하세요
    //
    // 설정할 값:
    //   - retryFor: RestClientException.class (이 예외 발생 시 재시도)
    //   - maxAttempts: 3 (최대 3번 시도)
    //   - backoff: @Backoff(delay = 1000, multiplier = 2.0)
    //     -> 1초 대기 -> 2초 대기 -> 실패 (지수 백오프)
    //
    // 힌트:
    //   @Retryable(
    //       retryFor = ???.class,
    //       maxAttempts = ???,
    //       backoff = @Backoff(delay = ???, multiplier = ???)
    //   )
    public Payment requestPayment(Order order) {
        log.info("[결제 요청] 주문 ID: {}, 금액: {}", order.getId(), order.getTotalAmount());

        RestTemplate restTemplate = createRestTemplate();

        // 멱등키 생성 (이중 결제 방지)
        // 주의: 재시도 시에도 동일한 멱등키를 사용해야 합니다!
        // UUID.randomUUID()를 매번 호출하면 재시도마다 다른 키가 되어 이중 결제 위험!
        // → 주문 ID 기반으로 고정된 키를 사용합니다.
        String idempotencyKey = "order-" + order.getId();

        // TODO 3: PG사 API를 호출하세요
        //
        // 요청 정보:
        //   - URL: pgBaseUrl + "/api/payments"
        //   - HTTP Method: POST
        //   - Request Body: Map.of(
        //         "orderId", order.getId(),
        //         "amount", order.getTotalAmount(),
        //         "idempotencyKey", idempotencyKey
        //     )
        //
        // 힌트:
        //   Map<String, Object> requestBody = Map.of(
        //       "orderId", order.getId(),
        //       "amount", order.getTotalAmount(),
        //       "idempotencyKey", idempotencyKey
        //   );
        //   ResponseEntity<Map> response = restTemplate.postForEntity(
        //       pgBaseUrl + "/api/payments",
        //       requestBody,
        //       Map.class
        //   );

        // TODO 4: 응답을 처리하여 Payment 엔티티를 생성하고 저장하세요
        //
        // 처리 흐름:
        //   1. response.getBody()에서 pgTransactionId를 추출
        //   2. Payment.builder()로 Payment 엔티티 생성
        //      - order, amount, idempotencyKey 설정
        //   3. payment.markAsSuccess(pgTransactionId) 호출
        //   4. paymentRepository.save(payment)로 저장
        //   5. 로그 출력: "[결제 성공] PG 트랜잭션: {}"
        //
        // 힌트:
        //   String pgTransactionId = (String) response.getBody().get("transactionId");
        //   Payment payment = Payment.builder()
        //       .order(order)
        //       .amount(order.getTotalAmount())
        //       .idempotencyKey(idempotencyKey)
        //       .build();
        //   payment.markAsSuccess(pgTransactionId);
        //   return paymentRepository.save(payment);

        return null; // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }

    // =========================================================================
    // 3단계: @Recover 폴백 메서드
    // =========================================================================

    /**
     * 결제 재시도 모두 실패 시 호출되는 폴백 메서드
     *
     * @Recover: @Retryable의 모든 재시도가 실패한 후 호출됩니다.
     * - 메서드 시그니처 규칙:
     *   1. 리턴 타입이 @Retryable 메서드와 동일해야 함
     *   2. 첫 번째 파라미터: 발생한 예외
     *   3. 나머지 파라미터: @Retryable 메서드와 동일
     */
    // TODO 5: @Recover 어노테이션을 추가하세요
    //
    // 힌트: @Recover
    public Payment recoverPayment(RestClientException ex, Order order) {
        log.error("[결제 실패] 모든 재시도 실패. 주문 ID: {}, 에러: {}", order.getId(), ex.getMessage());

        // TODO 6: 결제 실패 Payment 레코드를 생성하고 저장하세요
        //
        // 처리 흐름:
        //   1. Payment.builder()로 실패 상태의 Payment 생성
        //      - order, amount 설정
        //   2. payment.markAsFailed() 호출
        //   3. paymentRepository.save(payment)로 저장
        //   4. 저장된 payment 반환
        //
        // 힌트:
        //   Payment failedPayment = Payment.builder()
        //       .order(order)
        //       .amount(order.getTotalAmount())
        //       .build();
        //   failedPayment.markAsFailed();
        //   return paymentRepository.save(failedPayment);

        return null; // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }
}
