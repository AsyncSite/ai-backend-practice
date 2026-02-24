package com.gritmoments.backend.order.repository;

import com.gritmoments.backend.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 주문 리포지토리 (세션 02: JPA, 세션 03: 멱등키)
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 사용자의 주문 목록 (최신순, 페이지네이션) */
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 멱등키로 기존 주문 조회 (세션 03: 이중 결제 방지) */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /** 주문 상세 (연관 엔티티 한번에 로딩 - N+1 방지, 세션 02) */
    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.user " +
           "JOIN FETCH o.restaurant " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.menu " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);
}
