package com.gritmoments.backend.restaurant.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.common.dto.PageResponse;
import com.gritmoments.backend.restaurant.entity.Restaurant;
import com.gritmoments.backend.restaurant.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 가게 API 컨트롤러 (세션 10: 아키텍처, 세션 12: API 설계)
 *
 * REST API 설계 원칙:
 * - 리소스 중심 URL: /api/restaurants
 * - HTTP 메서드로 행위 표현: GET(조회), POST(생성)
 * - 페이지네이션: ?page=0&size=10&sort=name,asc
 */
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurant", description = "가게 API")
public class RestaurantController {

    private final RestaurantService restaurantService;

    /**
     * 가게 목록 조회 (세션 12: 페이지네이션)
     * GET /api/restaurants?page=0&size=10
     */
    @GetMapping
    @Operation(summary = "가게 목록 조회", description = "영업 중인 가게 목록을 페이지네이션으로 반환")
    public ResponseEntity<ApiResponse<PageResponse<Restaurant>>> getRestaurants(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 10) Pageable pageable) {

        var page = category != null
                ? restaurantService.getRestaurantsByCategory(category, pageable)
                : restaurantService.getRestaurants(pageable);

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    /**
     * 가게 상세 조회 (세션 01: 캐시 적용)
     * GET /api/restaurants/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "가게 상세 조회")
    public ResponseEntity<ApiResponse<Restaurant>> getRestaurant(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.getRestaurant(id)));
    }
}
