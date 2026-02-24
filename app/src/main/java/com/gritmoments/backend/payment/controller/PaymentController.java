package com.gritmoments.backend.payment.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.payment.entity.Payment;
import com.gritmoments.backend.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 API 컨트롤러 (세션 03: 외부 API 연동, 세션 10: 아키텍처)
 *
 * REST API 설계:
 * - POST /api/payments: 결제 요청 (멱등키로 중복 방지)
 * - GET /api/payments/order/{orderId}: 주문별 결제 조회
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 API")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 요청 (세션 03: 멱등성 보장)
     * POST /api/payments
     *
     * 멱등키(Idempotency Key)를 사용하여 동일한 요청이 여러 번 들어와도
     * 실제 결제는 한 번만 처리되도록 보장합니다.
     */
    @PostMapping
    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 요청합니다. 멱등키로 중복 결제를 방지합니다.")
    public ResponseEntity<ApiResponse<Payment>> requestPayment(@RequestBody PaymentRequest request) {
        Payment payment = paymentService.requestPayment(
                request.orderId(),
                request.amount(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok(ApiResponse.ok(payment));
    }

    /**
     * 결제 상태 조회 (세션 03: 외부연동)
     * GET /api/payments/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "결제 상태 조회", description = "결제 ID로 결제 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<Payment>> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getPayment(id)));
    }

    /**
     * 주문별 결제 조회
     * GET /api/payments/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    @Operation(summary = "주문별 결제 조회", description = "특정 주문의 결제 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<Payment>> getPaymentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.findByOrderId(orderId)));
    }

    /**
     * 결제 요청 DTO
     */
    public record PaymentRequest(
            Long orderId,
            Integer amount,
            String idempotencyKey
    ) {}
}
