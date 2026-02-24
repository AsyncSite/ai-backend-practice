package com.gritmoments.backend.restaurant.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.common.dto.PageResponse;
import com.gritmoments.backend.restaurant.entity.Restaurant;
import com.gritmoments.backend.restaurant.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * [Session 12 - Level 3] RESTful API 설계 - 가게 컨트롤러 직접 구현
 *
 * REST API 설계 원칙을 적용하여 가게 CRUD API를 구현합니다.
 *
 * REST 설계 핵심:
 *   1. 리소스 중심 URL: /api/restaurants (동사 X, 명사 O)
 *   2. HTTP 메서드로 행위 표현:
 *      - GET: 조회 (안전, 멱등)
 *      - POST: 생성 (비멱등)
 *      - PUT: 전체 수정 (멱등)
 *      - PATCH: 부분 수정 (멱등)
 *      - DELETE: 삭제 (멱등)
 *   3. 적절한 HTTP 상태 코드:
 *      - 200 OK: 성공
 *      - 201 Created: 리소스 생성 성공
 *      - 204 No Content: 성공했지만 반환할 내용 없음
 *      - 400 Bad Request: 잘못된 요청
 *      - 404 Not Found: 리소스 없음
 *   4. 페이지네이션: ?page=0&size=10&sort=name,asc
 *   5. HATEOAS: 응답에 관련 링크 포함
 *
 * TODO: 아래의 TODO 부분을 채워서 RESTful API를 완성하세요.
 */
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Restaurant", description = "가게 API")
public class RestaurantController {

    private final RestaurantService restaurantService;

    // =========================================================================
    // DTO 정의
    // =========================================================================

    /** 가게 생성 요청 */
    public record CreateRestaurantRequest(
            @NotBlank(message = "가게 이름은 필수입니다")
            String name,
            String category,
            String address,
            String phone,
            @Positive(message = "최소 주문 금액은 양수여야 합니다")
            Integer minOrderAmount,
            Integer deliveryFee
    ) {}

    /** 가게 수정 요청 */
    public record UpdateRestaurantRequest(
            String name,
            String category,
            String address,
            String phone,
            Integer minOrderAmount,
            Integer deliveryFee
    ) {}

    // =========================================================================
    // 1. GET - 목록 조회 (페이지네이션)
    // =========================================================================

    /**
     * 가게 목록 조회
     *
     * GET /api/restaurants?page=0&size=10&sort=name,asc&category=한식
     */
    // TODO 1: GET 매핑과 Swagger 문서화를 추가하세요
    //
    // 힌트:
    //   @GetMapping
    //   @Operation(summary = "가게 목록 조회", description = "영업 중인 가게 목록을 페이지네이션으로 반환")
    public ResponseEntity<ApiResponse<PageResponse<Restaurant>>> getRestaurants(
            @Parameter(description = "카테고리 필터 (예: 한식, 중식, 양식)")
            @RequestParam(required = false) String category,
            @PageableDefault(size = 10) Pageable pageable) {

        // TODO 2: 카테고리 파라미터에 따라 조건부 조회를 구현하세요
        //
        // 힌트:
        //   Page<Restaurant> page = category != null
        //       ? restaurantService.getRestaurantsByCategory(category, pageable)
        //       : restaurantService.getRestaurants(pageable);
        //
        //   return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(Page.empty()))); // TODO: 교체
    }

    // =========================================================================
    // 2. GET - 단건 조회
    // =========================================================================

    /**
     * 가게 상세 조회
     *
     * GET /api/restaurants/{id}
     */
    // TODO 3: GET 매핑과 Swagger 문서화를 추가하세요
    //
    // 힌트:
    //   @GetMapping("/{id}")
    //   @Operation(summary = "가게 상세 조회")
    public ResponseEntity<ApiResponse<Restaurant>> getRestaurant(
            @PathVariable Long id) {

        // TODO 4: 가게를 조회하고 응답하세요
        //
        // HTTP 상태 코드: 200 OK
        //
        // 힌트:
        //   Restaurant restaurant = restaurantService.getRestaurant(id);
        //   return ResponseEntity.ok(ApiResponse.ok(restaurant));

        return ResponseEntity.ok(ApiResponse.ok(null)); // TODO: 교체
    }

    // =========================================================================
    // 3. POST - 생성
    // =========================================================================

    /**
     * 가게 등록
     *
     * POST /api/restaurants
     * Body: { "name": "맛있는 집", "category": "한식", ... }
     */
    // TODO 5: POST 매핑과 Swagger 문서화를 추가하세요
    //
    // 힌트:
    //   @PostMapping
    //   @Operation(summary = "가게 등록", description = "새로운 가게를 등록합니다")
    public ResponseEntity<ApiResponse<Restaurant>> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request) {

        log.info("[가게 등록] 이름: {}", request.name());

        // TODO 6: 가게를 생성하고 201 Created로 응답하세요
        //
        // REST 원칙:
        //   - 리소스 생성 시 201 Created 반환
        //   - Location 헤더에 생성된 리소스의 URI 포함
        //
        // 힌트:
        //   Restaurant restaurant = Restaurant.builder()
        //       .name(request.name())
        //       .category(request.category())
        //       .address(request.address())
        //       .phone(request.phone())
        //       .minOrderAmount(request.minOrderAmount())
        //       .deliveryFee(request.deliveryFee())
        //       .build();
        //
        //   // restaurantService에 save 메서드가 있다고 가정
        //   // Restaurant saved = restaurantService.save(restaurant);
        //
        //   URI location = URI.create("/api/restaurants/" + saved.getId());
        //   return ResponseEntity.created(location).body(ApiResponse.ok(saved, "가게가 등록되었습니다"));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null)); // TODO: 교체
    }

    // =========================================================================
    // 4. PUT - 전체 수정
    // =========================================================================

    /**
     * 가게 정보 수정
     *
     * PUT /api/restaurants/{id}
     * Body: { "name": "새 이름", "category": "양식", ... }
     */
    // TODO 7: PUT 매핑을 추가하세요
    //
    // PUT vs PATCH:
    //   - PUT: 리소스 전체를 교체 (모든 필드 필수)
    //   - PATCH: 리소스 일부만 수정 (변경된 필드만)
    //
    // 힌트:
    //   @PutMapping("/{id}")
    //   @Operation(summary = "가게 정보 수정")
    public ResponseEntity<ApiResponse<Restaurant>> updateRestaurant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRestaurantRequest request) {

        log.info("[가게 수정] ID: {}, 이름: {}", id, request.name());

        // TODO 8: 가게 정보를 수정하고 200 OK로 응답하세요
        //
        // 힌트:
        //   Restaurant restaurant = restaurantService.getRestaurant(id);
        //   // 필드 업데이트 로직...
        //   // Restaurant updated = restaurantService.save(restaurant);
        //   return ResponseEntity.ok(ApiResponse.ok(updated, "가게 정보가 수정되었습니다"));

        return ResponseEntity.ok(ApiResponse.ok(null)); // TODO: 교체
    }

    // =========================================================================
    // 5. DELETE - 삭제
    // =========================================================================

    /**
     * 가게 삭제 (영업 종료)
     *
     * DELETE /api/restaurants/{id}
     */
    // TODO 9: DELETE 매핑을 추가하세요
    //
    // 삭제 성공 시 응답:
    //   - 204 No Content: 성공했지만 반환할 내용 없음 (일반적)
    //   - 200 OK + 메시지: 삭제 확인 메시지 반환 (선택)
    //
    // 힌트:
    //   @DeleteMapping("/{id}")
    //   @Operation(summary = "가게 삭제")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable Long id) {
        log.info("[가게 삭제] ID: {}", id);

        // TODO 10: 가게를 삭제하고 204 No Content로 응답하세요
        //
        // 소프트 삭제 vs 하드 삭제:
        //   - 소프트 삭제: isOpen = false (데이터 보존, 복구 가능)
        //   - 하드 삭제: DELETE FROM restaurants (데이터 영구 삭제)
        //   실무에서는 소프트 삭제를 권장합니다.
        //
        // 힌트:
        //   Restaurant restaurant = restaurantService.getRestaurant(id);
        //   restaurant.close();  // 소프트 삭제 (영업 종료)
        //   return ResponseEntity.noContent().build();

        return ResponseEntity.noContent().build(); // TODO: 실제 삭제 로직 추가
    }

    // =========================================================================
    // 6. HATEOAS (심화)
    // =========================================================================

    /**
     * HATEOAS 적용 - 가게 상세 조회
     *
     * HATEOAS(Hypermedia As The Engine Of Application State):
     *   응답에 관련 링크를 포함하여 클라이언트가 다음 행동을 알 수 있게 합니다.
     *
     * 예시 응답:
     * {
     *   "id": 1,
     *   "name": "맛있는 집",
     *   "_links": {
     *     "self": { "href": "/api/restaurants/1" },
     *     "menus": { "href": "/api/restaurants/1/menus" },
     *     "orders": { "href": "/api/restaurants/1/orders" }
     *   }
     * }
     */
    // TODO 11: HATEOAS를 적용한 조회 엔드포인트를 구현하세요
    //
    // 힌트:
    //   @GetMapping("/{id}/detail")
    //   @Operation(summary = "가게 상세 조회 (HATEOAS)")
    //   public ResponseEntity<EntityModel<Restaurant>> getRestaurantWithLinks(@PathVariable Long id) {
    //       Restaurant restaurant = restaurantService.getRestaurant(id);
    //
    //       EntityModel<Restaurant> model = EntityModel.of(restaurant,
    //           Link.of("/api/restaurants/" + id, "self"),
    //           Link.of("/api/restaurants/" + id + "/menus", "menus"),
    //           Link.of("/api/restaurants/" + id + "/orders", "orders")
    //       );
    //
    //       return ResponseEntity.ok(model);
    //   }

    // =========================================================================
    // 7. 에러 처리 (GlobalExceptionHandler와 연계)
    // =========================================================================

    // 에러 응답은 GlobalExceptionHandler에서 일괄 처리합니다.
    // ResourceNotFoundException -> 404 Not Found
    // BusinessException -> 400 Bad Request
    // MethodArgumentNotValidException -> 400 Bad Request (검증 실패)
    //
    // 일관된 에러 응답 형식:
    // {
    //   "success": false,
    //   "data": null,
    //   "message": "가게를 찾을 수 없습니다. ID: 99999"
    // }
}
