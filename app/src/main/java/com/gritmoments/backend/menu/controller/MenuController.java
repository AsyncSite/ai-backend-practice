package com.gritmoments.backend.menu.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.menu.entity.Menu;
import com.gritmoments.backend.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 메뉴 API 컨트롤러 (세션 01: 캐시, 세션 05: 동시성)
 *
 * GET /api/restaurants/{restaurantId}/menus - 메뉴 목록 (캐시 적용)
 * POST /api/menus/{menuId}/decrease-stock  - 재고 차감 (동시성 실습)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Menu", description = "메뉴 API")
public class MenuController {

    private final MenuService menuService;

    /**
     * 가게의 메뉴 목록 조회 (세션 01: 캐시 적용)
     * 첫 요청: DB 조회 -> Redis 캐시 저장 -> 응답
     * 이후 요청: Redis 캐시에서 바로 응답 (DB 조회 없음)
     */
    @GetMapping("/api/restaurants/{restaurantId}/menus")
    @Operation(summary = "메뉴 목록 조회", description = "해당 가게의 판매 가능한 메뉴 목록 (Redis 캐시 적용)")
    public ResponseEntity<ApiResponse<List<Menu>>> getMenus(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(menuService.getMenusByRestaurant(restaurantId)));
    }

    /**
     * 재고 차감 - 잠금 없음 (세션 05 L1: 경쟁 상태 재현)
     * 동시에 많은 요청이 오면 재고 정합성이 깨집니다!
     */
    @PostMapping("/api/menus/{menuId}/decrease-stock")
    @Operation(summary = "재고 차감 (잠금 없음)", description = "세션 05 L1: 동시 요청 시 경쟁 상태 발생")
    public ResponseEntity<ApiResponse<String>> decreaseStock(
            @PathVariable Long menuId,
            @RequestParam(defaultValue = "1") int quantity) {
        menuService.decreaseStockWithoutLock(menuId, quantity);
        return ResponseEntity.ok(ApiResponse.ok("재고 차감 완료"));
    }

    /**
     * 재고 차감 - 비관적 잠금 (세션 05 L2: 정합성 보장)
     * SELECT ... FOR UPDATE로 다른 트랜잭션 대기
     */
    @PostMapping("/api/menus/{menuId}/decrease-stock-pessimistic")
    @Operation(summary = "재고 차감 (비관적 잠금)", description = "세션 05 L2: FOR UPDATE로 정합성 보장")
    public ResponseEntity<ApiResponse<String>> decreaseStockPessimistic(
            @PathVariable Long menuId,
            @RequestParam(defaultValue = "1") int quantity) {
        menuService.decreaseStockWithPessimisticLock(menuId, quantity);
        return ResponseEntity.ok(ApiResponse.ok("재고 차감 완료 (비관적 잠금)"));
    }
}
