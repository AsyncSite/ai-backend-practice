package com.gritmoments.backend.payment.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Session 03 - Level 3] Resilience4j 서킷 브레이커 테스트
 *
 * 서킷 브레이커의 3가지 상태 전이를 테스트합니다:
 * 1. CLOSED: 정상 상태 (요청 통과)
 * 2. OPEN: 장애 감지 (요청 즉시 실패, 외부 호출 안 함)
 * 3. HALF_OPEN: 일부 요청만 통과하여 복구 확인
 *
 * TODO: 아래의 TODO 부분을 채워서 서킷 브레이커 동작을 테스트하세요.
 */
@SpringBootTest
public class CircuitBreakerTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private PaymentService paymentService;  // 서킷 브레이커가 적용된 서비스

    /**
     * 테스트 1: CLOSED 상태에서 정상 요청
     */
    @Test
    void testCircuitBreakerClosed() {
        // Given: 서킷 브레이커가 CLOSED 상태
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");

        // TODO 1: 초기 상태 확인
        // 힌트: circuitBreaker.getState() == CircuitBreaker.State.CLOSED
        // assertThat(circuitBreaker.getState()).isEqualTo(???);

        // When: 정상 요청 실행
        // TODO 2: paymentService.processPayment()를 호출하여 결과 확인
        // 힌트: 성공하면 예외가 발생하지 않음

        // Then: 여전히 CLOSED 상태 유지
        // TODO 3: 상태가 여전히 CLOSED인지 확인
    }

    /**
     * 테스트 2: 실패율 임계값 초과 시 OPEN 상태로 전이
     */
    @Test
    void testCircuitBreakerOpens() throws InterruptedException {
        // Given: 서킷 브레이커 초기화
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.reset();  // CLOSED 상태로 리셋

        // TODO 4: 설정 확인
        // application.yml에서 다음을 확인하세요:
        //   - failure-rate-threshold: 50% (실패율 50% 초과 시 OPEN)
        //   - minimum-number-of-calls: 5 (최소 5번 호출 후 판단)
        //   - wait-duration-in-open-state: 10s (OPEN 상태 유지 시간)

        // When: 의도적으로 5번 이상 실패 발생
        // TODO 5: paymentService를 10번 호출하되, 실패를 유도하세요
        // 힌트: 예외를 잡아서 무시하고 계속 호출 (try-catch)

        // for (int i = 0; i < 10; i++) {
        //     try {
        //         // TODO: 실패하는 요청 호출
        //     } catch (Exception e) {
        //         // 예외 무시
        //     }
        // }

        // Then: OPEN 상태로 전이 확인
        // TODO 6: 상태가 OPEN인지 확인
        // assertThat(circuitBreaker.getState()).isEqualTo(???);

        // TODO 7: OPEN 상태에서 요청 시 즉시 실패하는지 확인
        // 힌트: CallNotPermittedException 발생 예상
    }

    /**
     * 테스트 3: OPEN -> HALF_OPEN 전이
     */
    @Test
    void testCircuitBreakerHalfOpen() throws InterruptedException {
        // Given: 서킷 브레이커가 OPEN 상태
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.transitionToOpenState();  // 강제로 OPEN 상태로 전환

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When: wait-duration 대기 (OPEN 상태 유지 시간)
        // TODO 8: application.yml의 wait-duration-in-open-state 값만큼 대기
        // 힌트: Thread.sleep(11000); // 10초 + 여유 1초

        // TODO 9: 대기 후 요청을 보내면 HALF_OPEN 상태로 전이되는지 확인
        // 힌트: 첫 요청 시 HALF_OPEN으로 자동 전환됨

        // Then: HALF_OPEN 상태 확인
        // TODO 10: circuitBreaker.getState()가 HALF_OPEN인지 확인
    }

    /**
     * 테스트 4: HALF_OPEN -> CLOSED 복구
     */
    @Test
    void testCircuitBreakerRecovery() {
        // Given: 서킷 브레이커가 HALF_OPEN 상태
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.transitionToHalfOpenState();

        // TODO 11: application.yml에서 다음 설정 확인:
        //   - permitted-number-of-calls-in-half-open-state: 3
        //   - 3번의 요청 중 성공률이 50% 이상이면 CLOSED로 복구

        // When: HALF_OPEN 상태에서 성공 요청 3번
        // TODO 12: paymentService를 3번 호출하여 모두 성공시키기
        // 힌트: 모의 PG 서버가 랜덤이므로, 성공할 때까지 반복 가능

        // Then: CLOSED 상태로 복구
        // TODO 13: 상태가 CLOSED로 전이되었는지 확인
    }

    /**
     * 테스트 5: 서킷 브레이커 메트릭 확인
     */
    @Test
    void testCircuitBreakerMetrics() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.reset();

        // TODO 14: 몇 번의 요청 후 메트릭 확인
        // for (int i = 0; i < 10; i++) {
        //     try {
        //         paymentService.processPayment(???);
        //     } catch (Exception e) {
        //         // 무시
        //     }
        // }

        // TODO 15: 메트릭 출력
        // CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        // System.out.println("실패율: " + metrics.getFailureRate() + "%");
        // System.out.println("총 호출 수: " + metrics.getNumberOfSuccessfulCalls() +
        //                    " / " + metrics.getNumberOfFailedCalls());

        // TODO 16: 실패율이 임계값(50%)과 비교하여 OPEN 전이 여부 예측
    }
}


// ============================================
// 💡 서킷 브레이커 상태 전이 다이어그램
// ============================================
//
//                     실패율 임계값 초과
//   CLOSED  ─────────────────────────────────>  OPEN
//      ^                                          │
//      │                                          │ wait-duration 경과
//      │                                          │
//      │                                          v
//      │                                      HALF_OPEN
//      │                                          │
//      │                                          │
//      └──────────────────────────────────────────┘
//         성공률이 임계값 이상 (복구)
//
//
// ============================================
// 💡 주요 설정 파라미터
// ============================================
//
// failure-rate-threshold: 50
//   - 실패율 50% 초과 시 OPEN
//
// minimum-number-of-calls: 5
//   - 최소 5번 호출 후 실패율 계산
//
// wait-duration-in-open-state: 10s
//   - OPEN 상태 유지 시간
//
// permitted-number-of-calls-in-half-open-state: 3
//   - HALF_OPEN 상태에서 허용할 요청 수
//
// sliding-window-size: 10
//   - 최근 10번의 요청으로 실패율 계산


// ============================================
// 💡 실전 팁
// ============================================
//
// 1. 서킷 브레이커는 외부 시스템 장애로부터 내 시스템을 보호
// 2. Fallback 메서드를 함께 사용하여 대체 응답 제공
// 3. 모니터링: Actuator로 서킷 브레이커 상태 확인
//    GET /actuator/circuitbreakers
// 4. 로그: @CircuitBreaker에 fallbackMethod 지정 시 로그 확인
