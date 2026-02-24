package com.gritmoments.backend.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Session 10 - Level 3] Facade 패턴 구현
 *
 * 주문 생성 시 여러 서비스를 조율하는 Facade Service입니다.
 * 복잡한 비즈니스 로직을 단순한 인터페이스로 제공합니다.
 *
 * TODO: 아래의 TODO를 채워서 Facade 패턴을 완성하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFacadeService {

    // TODO 1: 필요한 서비스들을 주입받으세요
    // private final OrderService orderService;
    // private final MenuService menuService;
    // private final UserService userService;
    // private final PaymentService paymentService;
    // private final NotificationService notificationService;

    /**
     * 주문 생성의 전체 흐름을 조율합니다.
     *
     * 1. 사용자 검증
     * 2. 메뉴 재고 확인
     * 3. 주문 생성
     * 4. 결제 처리
     * 5. 알림 발송
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("[주문 Facade] 주문 생성 시작: {}", request);

        // TODO 2: 사용자 검증
        // User user = userService.findById(request.getUserId());
        // if (!user.isActive()) {
        //     throw new BusinessException("비활성 사용자");
        // }

        // TODO 3: 메뉴 재고 확인
        // for (OrderItem item : request.getItems()) {
        //     Menu menu = menuService.findById(item.getMenuId());
        //     if (!menu.isAvailable()) {
        //         throw new BusinessException("품절된 메뉴: " + menu.getName());
        //     }
        // }

        // TODO 4: 주문 생성
        // Order order = orderService.create(request);

        // TODO 5: 결제 처리
        // try {
        //     PaymentResult payment = paymentService.process(order);
        //     order.confirmPayment(payment.getTransactionId());
        // } catch (PaymentException e) {
        //     order.cancel();
        //     throw new BusinessException("결제 실패: " + e.getMessage());
        // }

        // TODO 6: 알림 발송 (비동기)
        // notificationService.sendOrderConfirmation(order);

        log.info("[주문 Facade] 주문 생성 완료");

        // TODO 7: 응답 DTO 생성 및 반환
        return null;
    }
}

// ============================================
// 💡 Facade 패턴의 장점
// ============================================
// 1. 복잡한 서브시스템을 단순한 인터페이스로 제공
// 2. 서브시스템 간 결합도 감소
// 3. 비즈니스 로직의 재사용성 향상
// 4. 트랜잭션 관리 단순화
