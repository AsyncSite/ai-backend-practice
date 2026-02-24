package com.gritmoments.backend.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [Session 13 - Level 3] Micrometer 커스텀 메트릭 설정
 *
 * 애플리케이션의 비즈니스 메트릭을 수집하여 모니터링합니다.
 *
 * 메트릭 3종류:
 *   1. Counter (카운터): 누적 값 (주문 수, 에러 수) - 증가만 가능
 *   2. Timer (타이머): 시간 측정 (결제 소요 시간, API 응답 시간)
 *   3. Gauge (게이지): 현재 값 (활성 사용자 수, 큐 대기 수) - 증가/감소 모두 가능
 *
 * RED 메서드 (모니터링 핵심 지표):
 *   - Rate: 초당 요청 수 (처리량)
 *   - Errors: 에러율 (안정성)
 *   - Duration: 응답 시간 (성능)
 *
 * Prometheus + Grafana 연동:
 *   - Micrometer가 메트릭 수집 -> /actuator/prometheus 엔드포인트 노출
 *   - Prometheus가 주기적으로 스크래핑 -> 시계열 데이터 저장
 *   - Grafana가 Prometheus에서 쿼리 -> 대시보드 시각화
 *
 * TODO: 아래의 TODO 부분을 채워서 커스텀 메트릭을 완성하세요.
 */
@Configuration
@Slf4j
public class MetricsConfig {

    // =========================================================================
    // Gauge용 상태 변수
    // =========================================================================

    /** 현재 활성 주문 수 (주문 생성 시 증가, 완료/취소 시 감소) */
    private final AtomicInteger activeOrderCount = new AtomicInteger(0);

    /** 현재 처리 대기 중인 메시지 수 */
    private final AtomicInteger pendingMessageCount = new AtomicInteger(0);

    // =========================================================================
    // 1단계: Counter 메트릭 (누적 값)
    // =========================================================================

    /**
     * 주문 생성 카운터
     *
     * Counter: 단조 증가하는 값 (절대 감소하지 않음)
     * - 주문이 생성될 때마다 1 증가
     * - Prometheus에서 rate()로 초당 주문 수 계산 가능
     *   예: rate(orders_created_total[5m]) = 5분간 평균 초당 주문 수
     */
    // TODO 1: 주문 생성 카운터를 등록하세요
    //
    // 힌트:
    //   @Bean
    //   public Counter orderCreatedCounter(MeterRegistry registry) {
    //       return Counter.builder("orders.created.total")
    //           .description("생성된 주문 총 수")
    //           .tag("type", "order")
    //           .register(registry);
    //   }

    /**
     * 주문 실패 카운터 (에러 추적)
     *
     * 에러 카운터는 알림 설정에 필수적입니다.
     * 예: 5분간 에러율 > 5% -> 슬랙 알림
     */
    // TODO 2: 주문 실패 카운터를 등록하세요
    //
    // 힌트:
    //   @Bean
    //   public Counter orderFailedCounter(MeterRegistry registry) {
    //       return Counter.builder("orders.failed.total")
    //           .description("실패한 주문 총 수")
    //           .tag("type", "order")
    //           .register(registry);
    //   }

    /**
     * 결제 카운터 (상태별)
     *
     * 태그(tag)로 결제 상태를 구분합니다.
     * - payments_total{status="success"}: 성공한 결제 수
     * - payments_total{status="failed"}: 실패한 결제 수
     */
    // TODO 3: 결제 성공/실패 카운터를 등록하세요
    //
    // 힌트:
    //   @Bean
    //   public Counter paymentSuccessCounter(MeterRegistry registry) {
    //       return Counter.builder("payments.total")
    //           .description("결제 처리 수")
    //           .tag("status", "success")
    //           .register(registry);
    //   }
    //
    //   @Bean
    //   public Counter paymentFailedCounter(MeterRegistry registry) {
    //       return Counter.builder("payments.total")
    //           .description("결제 처리 수")
    //           .tag("status", "failed")
    //           .register(registry);
    //   }

    // =========================================================================
    // 2단계: Timer 메트릭 (시간 측정)
    // =========================================================================

    /**
     * 결제 처리 시간 타이머
     *
     * Timer: 작업의 소요 시간과 호출 횟수를 함께 기록합니다.
     * - count: 호출 횟수
     * - sum: 총 소요 시간
     * - max: 최대 소요 시간
     * - histogram: 백분위수 계산 가능 (p50, p95, p99)
     *
     * 사용법:
     *   Timer.Sample sample = Timer.start(registry);
     *   // ... 결제 처리 ...
     *   sample.stop(paymentTimer);
     */
    // TODO 4: 결제 처리 시간 타이머를 등록하세요
    //
    // publishPercentileHistogram(true): p50, p95, p99 등 백분위수를 계산 가능
    //
    // 힌트:
    //   @Bean
    //   public Timer paymentTimer(MeterRegistry registry) {
    //       return Timer.builder("payment.duration")
    //           .description("결제 처리 소요 시간")
    //           .tag("method", "pg")
    //           .publishPercentileHistogram(true)
    //           .register(registry);
    //   }

    /**
     * 외부 API 호출 시간 타이머
     */
    // TODO 5: 외부 API 호출 시간 타이머를 등록하세요
    //
    // 힌트:
    //   @Bean
    //   public Timer externalApiTimer(MeterRegistry registry) {
    //       return Timer.builder("external.api.duration")
    //           .description("외부 API 호출 소요 시간")
    //           .tag("service", "pg")
    //           .publishPercentileHistogram(true)
    //           .register(registry);
    //   }

    // =========================================================================
    // 3단계: Gauge 메트릭 (현재 값)
    // =========================================================================

    /**
     * 현재 활성 주문 수 게이지
     *
     * Gauge: 현재 상태를 나타내는 값 (증가/감소 모두 가능)
     * - 활성 주문 수, 큐 대기 메시지 수, DB 커넥션 풀 사용 수 등
     * - 값이 특정 임계치를 초과하면 알림 설정 가능
     *
     * 사용법:
     *   activeOrderCount.incrementAndGet();  // 주문 생성 시
     *   activeOrderCount.decrementAndGet();  // 주문 완료/취소 시
     */
    // TODO 6: 활성 주문 수 게이지를 등록하세요
    //
    // 힌트:
    //   @Bean
    //   public Gauge activeOrderGauge(MeterRegistry registry) {
    //       return Gauge.builder("orders.active.count", activeOrderCount, AtomicInteger::get)
    //           .description("현재 활성(처리 중) 주문 수")
    //           .register(registry);
    //   }

    /**
     * 메시지 큐 대기 수 게이지
     */
    // TODO 7: 메시지 큐 대기 수 게이지를 등록하세요
    //
    // 힌트:
    //   @Bean
    //   public Gauge pendingMessageGauge(MeterRegistry registry) {
    //       return Gauge.builder("messages.pending.count", pendingMessageCount, AtomicInteger::get)
    //           .description("처리 대기 중인 메시지 수")
    //           .register(registry);
    //   }

    // =========================================================================
    // 헬퍼 메서드 (서비스에서 호출)
    // =========================================================================

    /** 활성 주문 증가 */
    public void incrementActiveOrders() {
        activeOrderCount.incrementAndGet();
    }

    /** 활성 주문 감소 */
    public void decrementActiveOrders() {
        activeOrderCount.decrementAndGet();
    }

    /** 대기 메시지 증가 */
    public void incrementPendingMessages() {
        pendingMessageCount.incrementAndGet();
    }

    /** 대기 메시지 감소 */
    public void decrementPendingMessages() {
        pendingMessageCount.decrementAndGet();
    }
}
