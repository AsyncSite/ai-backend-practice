package com.gritmoments.backend.review.service;

import com.gritmoments.backend.common.exception.BusinessException;
import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Session 10 - Level 3] 레이어드 아키텍처 - 리뷰 서비스 직접 구현
 *
 * 레이어드 아키텍처의 서비스 계층(Service Layer)을 직접 구현합니다.
 *
 * 아키텍처 구조:
 *   Controller (요청/응답 처리)
 *       ↓
 *   Service (비즈니스 로직)  <-- 이 클래스
 *       ↓
 *   Repository (데이터 접근)
 *
 * 서비스 계층의 책임:
 *   1. 비즈니스 로직 구현 (검증, 계산, 규칙 적용)
 *   2. 트랜잭션 관리 (@Transactional)
 *   3. DTO <-> Entity 변환
 *   4. 여러 Repository를 조합하여 복잡한 작업 수행
 *
 * TODO: 아래의 TODO 부분을 채워서 리뷰 서비스를 완성하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {

    // TODO 1: 필요한 Repository를 주입하세요
    //
    // 리뷰 서비스에 필요한 의존성:
    //   - ReviewRepository: 리뷰 CRUD
    //   - OrderRepository: 주문 존재 확인 (리뷰는 주문 후에만 작성 가능)
    //   - RestaurantRepository: 가게 존재 확인
    //
    // 힌트 (@RequiredArgsConstructor 사용으로 생성자 주입 자동 생성):
    //   private final ReviewRepository reviewRepository;
    //   private final OrderRepository orderRepository;
    //   private final RestaurantRepository restaurantRepository;

    // =========================================================================
    // DTO 정의 (서비스 내부에서 사용하는 데이터 전달 객체)
    // =========================================================================

    /**
     * 리뷰 생성 요청 DTO
     *
     * Controller -> Service로 데이터를 전달할 때 사용합니다.
     * Entity를 직접 노출하지 않고 DTO를 사용하는 이유:
     *   1. API 스펙과 DB 스키마를 분리 (독립적으로 변경 가능)
     *   2. 필요한 필드만 전달 (보안, 불필요한 데이터 차단)
     *   3. 입력 검증 어노테이션 적용 (@NotNull, @Size 등)
     */
    public record CreateReviewRequest(
            Long orderId,
            Long restaurantId,
            Long userId,
            int rating,        // 1~5 별점
            String content     // 리뷰 내용
    ) {}

    /**
     * 리뷰 응답 DTO
     *
     * Service -> Controller로 데이터를 반환할 때 사용합니다.
     */
    public record ReviewResponse(
            Long id,
            Long restaurantId,
            String restaurantName,
            Long userId,
            String userName,
            int rating,
            String content,
            String createdAt
    ) {}

    // =========================================================================
    // 비즈니스 로직 구현
    // =========================================================================

    /**
     * 리뷰 작성
     *
     * 비즈니스 규칙:
     *   1. 주문이 존재해야 함
     *   2. 주문이 완료(COMPLETED) 상태여야 함
     *   3. 같은 주문에 대해 중복 리뷰 불가
     *   4. 별점은 1~5 범위
     *   5. 리뷰 내용은 10자 이상
     */
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        log.info("[리뷰 작성] 주문 ID: {}, 가게 ID: {}", request.orderId(), request.restaurantId());

        // TODO 2: 입력 검증 - 별점 범위 확인
        //
        // 힌트:
        //   if (request.rating() < 1 || request.rating() > 5) {
        //       throw new BusinessException("별점은 1~5 사이여야 합니다.");
        //   }

        // TODO 3: 입력 검증 - 리뷰 내용 길이 확인
        //
        // 힌트:
        //   if (request.content() == null || request.content().length() < 10) {
        //       throw new BusinessException("리뷰 내용은 10자 이상이어야 합니다.");
        //   }

        // TODO 4: 비즈니스 규칙 - 주문 존재 확인
        //
        // 힌트:
        //   Order order = orderRepository.findById(request.orderId())
        //       .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        // TODO 5: 비즈니스 규칙 - 주문 완료 상태 확인
        //
        // 힌트:
        //   if (order.getStatus() != Order.OrderStatus.COMPLETED) {
        //       throw new BusinessException("완료된 주문에만 리뷰를 작성할 수 있습니다. 현재 상태: " + order.getStatus());
        //   }

        // TODO 6: 비즈니스 규칙 - 중복 리뷰 확인
        //
        // 힌트:
        //   if (reviewRepository.existsByOrderId(request.orderId())) {
        //       throw new BusinessException("이미 이 주문에 대한 리뷰가 존재합니다.");
        //   }

        // TODO 7: 가게 정보 조회
        //
        // 힌트:
        //   Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
        //       .orElseThrow(() -> new ResourceNotFoundException("Restaurant", request.restaurantId()));

        // TODO 8: Review Entity 생성 및 저장
        //
        // 힌트:
        //   Review review = Review.builder()
        //       .order(order)
        //       .restaurant(restaurant)
        //       .userId(request.userId())
        //       .rating(request.rating())
        //       .content(request.content())
        //       .build();
        //   Review savedReview = reviewRepository.save(review);

        // TODO 9: Entity -> DTO 변환 후 반환
        //
        // Entity를 직접 반환하지 않고 DTO로 변환하는 이유:
        //   - JPA 지연 로딩 문제 방지 (LazyInitializationException)
        //   - 불필요한 필드 노출 방지
        //   - API 응답 형식 제어
        //
        // 힌트:
        //   return new ReviewResponse(
        //       savedReview.getId(),
        //       restaurant.getId(),
        //       restaurant.getName(),
        //       request.userId(),
        //       "사용자",  // 실제로는 User 조회 필요
        //       savedReview.getRating(),
        //       savedReview.getContent(),
        //       savedReview.getCreatedAt().toString()
        //   );

        return null; // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }

    /**
     * 가게의 리뷰 목록 조회 (페이지네이션)
     */
    public Page<ReviewResponse> getReviewsByRestaurant(Long restaurantId, Pageable pageable) {
        log.info("[리뷰 조회] 가게 ID: {}", restaurantId);

        // TODO 10: 가게 존재 확인 후 리뷰 목록 조회
        //
        // 처리 흐름:
        //   1. restaurantRepository.findById()로 가게 존재 확인
        //   2. reviewRepository.findByRestaurantId(restaurantId, pageable)로 리뷰 조회
        //   3. Page<Review> -> Page<ReviewResponse> 변환
        //
        // 힌트:
        //   restaurantRepository.findById(restaurantId)
        //       .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        //
        //   return reviewRepository.findByRestaurantId(restaurantId, pageable)
        //       .map(review -> new ReviewResponse(
        //           review.getId(),
        //           restaurantId,
        //           "가게명",  // 이미 알고 있는 값
        //           review.getUserId(),
        //           "사용자",
        //           review.getRating(),
        //           review.getContent(),
        //           review.getCreatedAt().toString()
        //       ));

        return Page.empty(); // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }

    /**
     * 가게 평균 별점 계산
     */
    public double getAverageRating(Long restaurantId) {
        log.info("[평균 별점] 가게 ID: {}", restaurantId);

        // TODO 11: 가게의 평균 별점을 계산하세요
        //
        // 힌트:
        //   Double average = reviewRepository.getAverageRatingByRestaurantId(restaurantId);
        //   return average != null ? Math.round(average * 10) / 10.0 : 0.0;

        return 0.0; // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }
}
