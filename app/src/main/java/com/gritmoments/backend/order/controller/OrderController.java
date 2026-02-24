package com.gritmoments.backend.order.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.menu.entity.Menu;
import com.gritmoments.backend.menu.repository.MenuRepository;
import com.gritmoments.backend.notification.NotificationService;
import com.gritmoments.backend.order.entity.Order;
import com.gritmoments.backend.order.entity.OrderItem;
import com.gritmoments.backend.order.service.OrderService;
import com.gritmoments.backend.restaurant.entity.Restaurant;
import com.gritmoments.backend.restaurant.repository.RestaurantRepository;
import com.gritmoments.backend.user.entity.User;
import com.gritmoments.backend.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 주문 API 컨트롤러 (세션 10: 아키텍처, 세션 12: API 설계)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "주문 API")
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuRepository menuRepository;
    private final NotificationService notificationService;

    /** 주문 상세 조회 */
    @GetMapping("/{id}")
    @Operation(summary = "주문 상세 조회")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrder(id)));
    }

    /** 주문 상태 변경 */
    @PatchMapping("/{id}/status")
    @Operation(summary = "주문 상태 변경")
    public ResponseEntity<ApiResponse<Order>> updateStatus(
            @PathVariable Long id,
            @RequestParam Order.OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.updateOrderStatus(id, status)));
    }

    /** 주문 생성 (세션 04: 비동기 처리) */
    @PostMapping
    @Operation(summary = "주문 생성")
    public ResponseEntity<ApiResponse<Order>> createOrder(@RequestBody OrderCreateRequest request) {
        log.info("[주문 생성] 사용자: {}, 가게: {}, 항목 수: {}",
                request.userId(), request.restaurantId(), request.items().size());

        // 엔티티 조회
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.userId()));
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new IllegalArgumentException("가게를 찾을 수 없습니다: " + request.restaurantId()));

        // 주문 엔티티 생성
        Order order = Order.builder()
                .user(user)
                .restaurant(restaurant)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        // 주문 항목 추가
        for (OrderItemRequest itemRequest : request.items()) {
            Menu menu = menuRepository.findById(itemRequest.menuId())
                    .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + itemRequest.menuId()));

            OrderItem orderItem = OrderItem.builder()
                    .menu(menu)
                    .quantity(itemRequest.quantity())
                    .build();

            order.addItem(orderItem);
        }

        // 주문 저장
        Order savedOrder = orderService.createOrder(order);
        log.info("[주문 생성 완료] 주문 ID: {}, 총액: {}원", savedOrder.getId(), savedOrder.getTotalAmount());

        // RabbitMQ를 통한 비동기 알림 발행 (세션 04)
        notificationService.publishOrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getUser().getId(),
                savedOrder.getRestaurant().getName(),
                savedOrder.getTotalAmount()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(savedOrder));
    }

    /** 주문 생성 요청 DTO */
    public record OrderCreateRequest(
            Long userId,
            Long restaurantId,
            List<OrderItemRequest> items
    ) {}

    /** 주문 항목 요청 DTO */
    public record OrderItemRequest(
            Long menuId,
            Integer quantity
    ) {}
}
