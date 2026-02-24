package com.gritmoments.backend.restaurant.repository;

import com.gritmoments.backend.restaurant.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 가게 리포지토리 (세션 02: Spring Data JPA, 세션 12: 페이지네이션)
 */
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    /** 카테고리별 가게 목록 (페이지네이션) */
    Page<Restaurant> findByCategory(String category, Pageable pageable);

    /** 영업 중인 가게 목록 */
    Page<Restaurant> findByIsOpenTrue(Pageable pageable);
}
