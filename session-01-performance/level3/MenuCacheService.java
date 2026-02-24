package com.gritmoments.backend.menu.service;

import com.gritmoments.backend.menu.entity.Menu;
import com.gritmoments.backend.menu.repository.MenuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * [Session 01 - Level 3] cache-aside 패턴 직접 구현
 *
 * @Cacheable 어노테이션을 사용하지 않고,
 * RedisTemplate을 사용하여 캐시를 직접 관리합니다.
 *
 * TODO: 아래의 TODO 부분을 채워서 cache-aside 패턴을 완성하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuCacheService {

    private final MenuRepository menuRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "menus:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * cache-aside 패턴으로 메뉴 목록 조회
     *
     * 흐름:
     * 1. Redis에서 캐시 조회
     * 2. 캐시 히트 -> 캐시 데이터 반환
     * 3. 캐시 미스 -> DB 조회 -> Redis에 저장 -> 반환
     */
    public List<Menu> getMenusByRestaurantCached(Long restaurantId) {
        String cacheKey = CACHE_PREFIX + restaurantId;

        // TODO 1: Redis에서 캐시 데이터 조회
        // 힌트: redisTemplate.opsForValue().get(cacheKey)

        // TODO 2: 캐시 히트인 경우 (데이터가 null이 아닌 경우)
        //   - 로그 출력: "[캐시 히트] 가게 {} 메뉴 목록"
        //   - JSON 문자열을 List<Menu>로 역직렬화하여 반환
        //   - 힌트: objectMapper.readValue(jsonString, new TypeReference<List<Menu>>(){})

        // TODO 3: 캐시 미스인 경우
        //   - 로그 출력: "[캐시 미스] 가게 {} 메뉴 목록 - DB 조회"
        //   - DB에서 조회: menuRepository.findByRestaurantIdAndIsAvailableTrue(restaurantId)
        //   - 조회 결과를 JSON 문자열로 직렬화
        //   - Redis에 저장 (TTL 포함)
        //   - 힌트: redisTemplate.opsForValue().set(cacheKey, jsonString, DEFAULT_TTL)
        //   - 조회 결과 반환

        return List.of(); // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }

    /**
     * 캐시 무효화 (메뉴가 변경되었을 때 호출)
     */
    public void evictMenuCache(Long restaurantId) {
        String cacheKey = CACHE_PREFIX + restaurantId;

        // TODO 4: Redis에서 해당 키 삭제
        // 힌트: redisTemplate.delete(cacheKey)

        log.info("[캐시 삭제] 가게 {} 메뉴 캐시 무효화", restaurantId);
    }
}
