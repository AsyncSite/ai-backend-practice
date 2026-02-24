package com.gritmoments.backend.payment.repository;

import com.gritmoments.backend.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 결제 리포지토리 (세션 03: 외부연동)
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /** 멱등키로 기존 결제 조회 (이중 결제 방지) */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
