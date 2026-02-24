package com.gritmoments.backend.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [Session 13 - Level 3] Micrometer로 커스텀 메트릭 구현
 *
 * 주문 관련 비즈니스 메트릭을 수집합니다.
 * - 주문 수 카운터
 * - 주문 처리 시간
 * - 주문 금액 분포
 *
 * TODO: 아래의 TODO를 채워서 커스텀 메트릭을 완성하세요.
 */
@Service
@Slf4j
public class OrderMetricsService {

    private final MeterRegistry meterRegistry;

    // TODO 1: 메트릭 객체 선언
    private final Counter orderCreatedCounter;
    private final Counter orderCancelledCounter;
    private final Timer orderProcessingTimer;

    public OrderMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // TODO 2: 주문 생성 카운터
        // 힌트: Counter.builder("orders.created")
        //           .description("Total number of orders created")
        //           .tag("type", "all")
        //           .register(meterRegistry);
        this.orderCreatedCounter = Counter.builder("orders.created")
            .description("Total number of orders created")
            .register(meterRegistry);

        // TODO 3: 주문 취소 카운터
        this.orderCancelledCounter = Counter.builder("orders.cancelled")
            .description("Total number of orders cancelled")
            .register(meterRegistry);

        // TODO 4: 주문 처리 시간 타이머
        // 힌트: Timer.builder("orders.processing.time")
        //           .description("Order processing time")
        //           .register(meterRegistry);
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .register(meterRegistry);
    }

    /**
     * 주문 생성 시 호출
     */
    public void recordOrderCreated(String restaurantType, double amount) {
        // TODO 5: 카운터 증가
        // 힌트: orderCreatedCounter.increment();
        orderCreatedCounter.increment();

        // TODO 6: 레스토랑 타입별 카운터 (태그 활용)
        // 힌트: Counter.builder("orders.created.by.type")
        //           .tag("restaurant_type", restaurantType)
        //           .register(meterRegistry)
        //           .increment();
        Counter.builder("orders.created.by.type")
            .tag("restaurant_type", restaurantType)
            .register(meterRegistry)
            .increment();

        // TODO 7: 주문 금액 게이지 (Gauge)
        // 힌트: meterRegistry.gauge("orders.amount", amount);
        log.info("[메트릭] 주문 생성: type={}, amount={}", restaurantType, amount);
    }

    /**
     * 주문 처리 시간 측정
     */
    public void recordOrderProcessingTime(Runnable orderProcessing) {
        // TODO 8: Timer로 처리 시간 측정
        // 힌트: orderProcessingTimer.record(orderProcessing);
        orderProcessingTimer.record(orderProcessing);
    }

    /**
     * 주문 취소 시 호출
     */
    public void recordOrderCancelled(String reason) {
        // TODO 9: 취소 사유별 카운터
        // 힌트: Counter.builder("orders.cancelled")
        //           .tag("reason", reason)
        //           .register(meterRegistry)
        //           .increment();
        Counter.builder("orders.cancelled.by.reason")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();

        log.info("[메트릭] 주문 취소: reason={}", reason);
    }
}


// ============================================
// 💡 Micrometer 메트릭 타입
// ============================================
//
// 1. Counter (카운터):
//    - 단조 증가하는 값 (총 주문 수, 총 방문자 수)
//    - increment() 메서드 사용
//
// 2. Gauge (게이지):
//    - 증가/감소 가능한 값 (현재 연결 수, 온도)
//    - 특정 값을 관찰
//
// 3. Timer (타이머):
//    - 이벤트 발생 횟수와 총 소요 시간
//    - 자동으로 평균, 최대, 백분위 계산
//
// 4. DistributionSummary (분포 요약):
//    - 이벤트 분포 (요청 크기, 응답 크기)


// ============================================
// 💡 사용 예시 (OrderService.java)
// ============================================
//
// @Service
// @RequiredArgsConstructor
// public class OrderService {
//
//     private final OrderMetricsService metricsService;
//
//     public Order createOrder(OrderRequest request) {
//         // 주문 처리 시간 측정
//         metricsService.recordOrderProcessingTime(() -> {
//             Order order = orderRepository.save(newOrder);
//             // ... 주문 처리 로직
//         });
//
//         // 주문 생성 메트릭 기록
//         metricsService.recordOrderCreated("korean", order.getTotalAmount());
//
//         return order;
//     }
//
//     public void cancelOrder(Long orderId, String reason) {
//         // ... 취소 로직
//         metricsService.recordOrderCancelled(reason);
//     }
// }


// ============================================
// 💡 Prometheus 쿼리 예시
// ============================================
//
// # 분당 주문 생성 수
// rate(orders_created_total[1m])
//
// # 레스토랑 타입별 주문 수
// sum by (restaurant_type) (orders_created_by_type_total)
//
// # 주문 처리 시간 평균
// rate(orders_processing_time_sum[5m]) / rate(orders_processing_time_count[5m])
//
// # 주문 처리 시간 99백분위
// histogram_quantile(0.99, rate(orders_processing_time_bucket[5m]))
//
// # 취소 사유별 비율
// sum by (reason) (rate(orders_cancelled_by_reason_total[5m]))


// ============================================
// 💡 Grafana 대시보드 구성
// ============================================
//
// 패널 1: 주문 생성 추이 (시계열)
// - Query: rate(orders_created_total[1m])
//
// 패널 2: 레스토랑 타입별 주문 (파이 차트)
// - Query: sum by (restaurant_type) (orders_created_by_type_total)
//
// 패널 3: 주문 처리 시간 (히트맵)
// - Query: orders_processing_time_bucket
//
// 패널 4: 취소율 (게이지)
// - Query: rate(orders_cancelled_total[5m]) / rate(orders_created_total[5m])
