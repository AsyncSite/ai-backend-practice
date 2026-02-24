package com.gritmoments.backend.payment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gritmoments.backend.order.entity.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 엔티티 (세션 03: 외부연동)
 *
 * 외부 PG(Payment Gateway)를 통한 결제 결과를 저장합니다.
 * - 멱등키: 동일 요청의 중복 결제 방지 (세션 03)
 * - PG 트랜잭션 ID: 외부 시스템과의 대조용
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order", columnList = "order_id"),
        @Index(name = "idx_payments_idempotency", columnList = "idempotency_key", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** PG사에서 발급한 트랜잭션 ID */
    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    /** 멱등키 - 같은 키로 여러 번 결제 요청해도 1번만 처리 */
    @Column(name = "idempotency_key", unique = true, length = 50)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PaymentStatus {
        PENDING,    // 결제 대기
        SUCCESS,    // 결제 성공
        FAILED,     // 결제 실패
        REFUNDED    // 환불 완료
    }

    @Builder
    public Payment(Order order, Integer amount, String idempotencyKey) {
        this.order = order;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
    }

    public void markAsSuccess(String pgTransactionId) {
        this.status = PaymentStatus.SUCCESS;
        this.pgTransactionId = pgTransactionId;
    }

    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markAsRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
