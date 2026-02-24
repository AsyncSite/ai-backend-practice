package com.gritmoments.backend.menu.service;

import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import com.gritmoments.backend.menu.entity.Menu;
import com.gritmoments.backend.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 메뉴 서비스 (세션 01: 캐시, 세션 05: 동시성)
 *
 * 핵심 학습 포인트:
 * - @Cacheable: 캐시에 데이터가 있으면 DB를 조회하지 않음 (세션 01)
 * - @CacheEvict: 데이터가 변경되면 캐시를 무효화 (세션 01)
 * - 비관적 잠금: 동시 재고 차감 시 정합성 보장 (세션 05)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;

    /**
     * 가게의 메뉴 목록 조회 (세션 01: 캐시 적용)
     *
     * @Cacheable: 첫 호출은 DB에서 조회, 이후는 Redis 캐시에서 반환
     * - value: 캐시 이름 ("menus" -> TTL 10분, RedisConfig에서 설정)
     * - key: 캐시 키 (가게 ID)
     */
    @Cacheable(value = "menus", key = "#restaurantId")
    public List<Menu> getMenusByRestaurant(Long restaurantId) {
        log.info("[DB 조회] 가게 {} 메뉴 목록 - 캐시 미스(MISS)", restaurantId);
        return menuRepository.findByRestaurantIdAndIsAvailableTrue(restaurantId);
    }

    /**
     * 메뉴 단건 조회
     */
    public Menu getMenu(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
    }

    /**
     * 재고 차감 - 잠금 없음 (세션 05 L1: 경쟁 상태 재현용)
     *
     * 주의: 동시 요청 시 재고 정합성이 깨질 수 있습니다!
     * 이 메서드는 의도적으로 잠금을 사용하지 않습니다.
     */
    @Transactional
    public void decreaseStockWithoutLock(Long menuId, int quantity) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        menu.decreaseStock(quantity);
        // JPA dirty checking으로 자동 UPDATE
    }

    /**
     * 재고 차감 - 비관적 잠금 (세션 05 L2: 정합성 보장)
     *
     * @Lock(PESSIMISTIC_WRITE) = SELECT ... FOR UPDATE
     * 다른 트랜잭션이 같은 행을 수정하지 못하도록 잠금
     */
    @Transactional
    public void decreaseStockWithPessimisticLock(Long menuId, int quantity) {
        Menu menu = menuRepository.findByIdWithPessimisticLock(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        menu.decreaseStock(quantity);
    }

    /**
     * 메뉴 정보 변경 시 캐시 무효화 (세션 01)
     *
     * @CacheEvict: 해당 가게의 메뉴 캐시를 삭제
     * 다음 조회 시 DB에서 최신 데이터를 다시 로드
     */
    @Transactional
    @CacheEvict(value = "menus", key = "#menu.restaurant.id")
    public Menu updateMenu(Menu menu) {
        return menuRepository.save(menu);
    }
}
