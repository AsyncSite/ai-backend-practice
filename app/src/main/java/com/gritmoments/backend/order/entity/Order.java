package com.gritmoments.backend.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gritmoments.backend.restaurant.entity.Restaurant;
import com.gritmoments.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 엔티티 (세션 03: 외부연동, 세션 04: 비동기, 세션 05: 동시성)
 *
 * 주문은 서비스의 핵심 트랜잭션입니다.
 * - 재고 차감 (세션 05: 동시성 제어)
 * - 결제 요청 (세션 03: 외부 API 연동)
 * - 알림 발송 (세션 04: 비동기 처리)
 * - 상태 관리 (PENDING -> PAID -> PREPARING -> DELIVERING -> COMPLETED)
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id"),
        @Index(name = "idx_orders_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_orders_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * 멱등키 (세션 03: 외부연동 - 이중 결제 방지)
     * 같은 멱등키로 여러 번 요청해도 결제는 1번만 처리
     */
    @Column(name = "idempotency_key", unique = true, length = 50)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 주문 상태 (상태 전이 규칙을 코드로 강제) */
    public enum OrderStatus {
        PENDING,     // 주문 생성 (결제 전)
        PAID,        // 결제 완료
        PREPARING,   // 조리 중
        DELIVERING,  // 배달 중
        COMPLETED,   // 완료
        CANCELLED    // 취소
    }

    @Builder
    public Order(User user, Restaurant restaurant, String idempotencyKey) {
        this.user = user;
        this.restaurant = restaurant;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING;
    }

    /** 주문 항목 추가 */
    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
        recalculateTotal();
    }

    /** 총액 재계산 */
    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    /** 결제 완료 처리 */
    public void markAsPaid() {
        validateStatusTransition(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
    }

    /** 조리 시작 */
    public void markAsPreparing() {
        validateStatusTransition(OrderStatus.PREPARING);
        this.status = OrderStatus.PREPARING;
    }

    /** 배달 시작 */
    public void markAsDelivering() {
        validateStatusTransition(OrderStatus.DELIVERING);
        this.status = OrderStatus.DELIVERING;
    }

    /** 완료 */
    public void markAsCompleted() {
        validateStatusTransition(OrderStatus.COMPLETED);
        this.status = OrderStatus.COMPLETED;
    }

    /** 취소 */
    public void cancel() {
        if (this.status == OrderStatus.DELIVERING || this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("배달 중이거나 완료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    /** 상태 전이 유효성 검사 */
    private void validateStatusTransition(OrderStatus newStatus) {
        boolean valid = switch (newStatus) {
            case PAID -> this.status == OrderStatus.PENDING;
            case PREPARING -> this.status == OrderStatus.PAID;
            case DELIVERING -> this.status == OrderStatus.PREPARING;
            case COMPLETED -> this.status == OrderStatus.DELIVERING;
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                    "주문 상태를 " + this.status + "에서 " + newStatus + "로 변경할 수 없습니다.");
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
