package com.gritmoments.backend.menu.repository;

import com.gritmoments.backend.menu.entity.Menu;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 메뉴 리포지토리 (세션 01: 캐시, 세션 05: 동시성)
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** 가게의 메뉴 목록 (판매 가능한 것만) */
    List<Menu> findByRestaurantIdAndIsAvailableTrue(Long restaurantId);

    /** 가게의 전체 메뉴 목록 */
    List<Menu> findByRestaurantId(Long restaurantId);

    /**
     * 비관적 잠금으로 메뉴 조회 (세션 05: 동시성 - Level 2)
     *
     * SELECT ... FOR UPDATE: 다른 트랜잭션이 이 행을 수정하지 못하도록 잠금
     * 재고 차감 시 정합성을 보장하기 위해 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Menu m WHERE m.id = :id")
    Optional<Menu> findByIdWithPessimisticLock(@Param("id") Long id);
}
