package com.gritmoments.backend.restaurant.service;

import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import com.gritmoments.backend.restaurant.entity.Restaurant;
import com.gritmoments.backend.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가게 서비스 (세션 01: 캐시, 세션 12: 페이지네이션)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    /**
     * 가게 목록 조회 (페이지네이션, 세션 12)
     */
    public Page<Restaurant> getRestaurants(Pageable pageable) {
        return restaurantRepository.findByIsOpenTrue(pageable);
    }

    /**
     * 카테고리별 가게 목록
     */
    public Page<Restaurant> getRestaurantsByCategory(String category, Pageable pageable) {
        return restaurantRepository.findByCategory(category, pageable);
    }

    /**
     * 가게 상세 조회 (세션 01: 캐시 적용)
     */
    @Cacheable(value = "restaurants", key = "#id")
    public Restaurant getRestaurant(Long id) {
        log.info("[DB 조회] 가게 {} 상세 - 캐시 미스(MISS)", id);
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
    }
}
