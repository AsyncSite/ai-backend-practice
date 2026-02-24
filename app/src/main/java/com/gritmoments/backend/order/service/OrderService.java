package com.gritmoments.backend.order.service;

import com.gritmoments.backend.common.exception.BusinessException;
import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import com.gritmoments.backend.order.entity.Order;
import com.gritmoments.backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 서비스 (세션 03: 외부연동, 세션 04: 비동기, 세션 05: 동시성)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    /** 주문 상세 조회 (N+1 방지, 세션 02) */
    public Order getOrder(Long orderId) {
        return orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    /** 사용자의 주문 목록 */
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 멱등키로 기존 주문 확인 (세션 03: 이중 결제 방지)
     * 같은 멱등키의 주문이 이미 존재하면 새 주문을 생성하지 않음
     */
    public Order findByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    }

    /** 주문 저장 */
    @Transactional
    public Order createOrder(Order order) {
        // 멱등키 중복 확인
        if (order.getIdempotencyKey() != null) {
            Order existing = findByIdempotencyKey(order.getIdempotencyKey());
            if (existing != null) {
                log.warn("중복 주문 요청 감지. 멱등키: {}", order.getIdempotencyKey());
                return existing;
            }
        }
        return orderRepository.save(order);
    }

    /** 주문 상태 변경 */
    @Transactional
    public Order updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        switch (newStatus) {
            case PAID -> order.markAsPaid();
            case PREPARING -> order.markAsPreparing();
            case DELIVERING -> order.markAsDelivering();
            case COMPLETED -> order.markAsCompleted();
            case CANCELLED -> order.cancel();
            default -> throw new BusinessException("지원하지 않는 상태 변경: " + newStatus);
        }
        return order;
    }
}
