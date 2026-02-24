# [Session 10] 아키텍처 패턴 -- 계층형 구조로 CRUD API 만들기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Controller -> Service -> Repository 3계층 구조를 이해한다
- 제공된 프로젝트의 패키지 구조를 분석할 수 있다
- 기존 패턴을 따라 새 모듈(기능)을 추가할 수 있다
- 모듈형 모놀리스(Modular Monolith) 구조를 설계할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **exercises/ 디렉토리에서** Docker Compose 실행
- 기본 인프라 실행: `docker compose up -d` (MySQL + Redis + App)
- 컨테이너 이름: `grit-app`, `grit-mysql`, `grit-redis`

## 핵심 개념

```
3계층 아키텍처 (Layered Architecture):

요청 -> Controller (HTTP 처리)
           ↓
        Service (비즈니스 로직)
           ↓
        Repository (DB 접근)
           ↓
        Database

모듈형 구조:
  user/       회원 모듈 (독립적)
  restaurant/ 가게 모듈 (독립적)
  order/      주문 모듈 (user, restaurant 참조)
```

---

## Level 1: 따라하기 -- 프로젝트 구조 분석

### Step 1: 패키지 구조 확인

```bash
# 프로젝트 루트로 이동
cd exercises/

# 앱 패키지 구조 확인
tree app/src/main/java/com/gritmoments/backend -L 2
```

**예상 출력**:
```
app/src/main/java/com/gritmoments/backend/
├── BackendApplication.java     # 엔트리포인트
├── common/                     # 공통 (설정, 예외, DTO)
│   ├── config/
│   ├── dto/
│   └── exception/
├── user/                       # 회원 모듈
│   ├── entity/
│   ├── repository/
│   ├── service/
│   └── controller/
├── restaurant/                 # 가게 모듈
├── menu/                       # 메뉴 모듈
├── order/                      # 주문 모듈
└── payment/                    # 결제 모듈
```

### Step 2: Restaurant 모듈 코드 분석

```bash
# Entity 확인
cat app/src/main/java/com/gritmoments/backend/restaurant/entity/Restaurant.java

# Repository 확인
cat app/src/main/java/com/gritmoments/backend/restaurant/repository/RestaurantRepository.java

# Service 확인
cat app/src/main/java/com/gritmoments/backend/restaurant/service/RestaurantService.java

# Controller 확인
cat app/src/main/java/com/gritmoments/backend/restaurant/controller/RestaurantController.java
```

**관찰 포인트**:
- **Entity**: DB 테이블과 매핑되는 도메인 객체 (`@Entity`, `@Table`)
- **Repository**: DB 조회를 위한 인터페이스 (`JpaRepository` 상속)
- **Service**: 비즈니스 로직 처리 (`@Service`, `@Transactional`)
- **Controller**: HTTP 요청/응답 처리 (`@RestController`, `@GetMapping`)

### Step 3: API 호출로 계층 흐름 확인

```bash
# 앱 서버 실행 확인
docker compose ps

# 가게 목록 조회 (Controller -> Service -> Repository)
curl http://localhost:8080/api/restaurants | python3 -m json.tool

# 특정 가게 조회
curl http://localhost:8080/api/restaurants/1 | python3 -m json.tool
```

**예상 출력**: JSON 형식의 가게 데이터가 반환됩니다.

### Step 4: 앱 로그에서 계층별 동작 확인

```bash
# 로그에서 SQL 쿼리 확인
docker logs grit-app --tail 30 | grep -i select

# 로그에서 HTTP 요청 확인
docker logs grit-app --tail 30 | grep -i "GET /api"
```

**관찰 포인트**:
1. Controller가 HTTP 요청 수신
2. Service가 비즈니스 로직 처리
3. Repository가 SQL 실행
4. 결과가 역순으로 반환

### Step 5: Swagger UI에서 API 확인

```bash
# 브라우저에서 열기
open http://localhost:8080/swagger-ui.html
```

**예상 화면**: 모든 REST API 엔드포인트가 그룹별로 정리되어 표시됩니다.

---

## Level 2: 변형하기 -- 새 모듈 추가

### Step 1: Review 엔티티 생성

기존 패턴을 따라 "리뷰(Review)" 모듈을 추가합니다.

`app/src/main/java/com/gritmoments/backend/review/entity/Review.java` 생성:

```java
package com.gritmoments.backend.review.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long restaurantId;
    private Integer rating;  // 1-5

    @Column(length = 500)
    private String content;

    @CreatedDate
    private LocalDateTime createdAt;

    // Constructor, Builder 등 추가
}
```

### Step 2: ReviewRepository 생성

`app/src/main/java/com/gritmoments/backend/review/repository/ReviewRepository.java`:

```java
package com.gritmoments.backend.review.repository;

import com.gritmoments.backend.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByRestaurantId(Long restaurantId);
    List<Review> findByUserId(Long userId);
}
```

### Step 3: ReviewService 생성

`app/src/main/java/com/gritmoments/backend/review/service/ReviewService.java`:

```java
package com.gritmoments.backend.review.service;

import com.gritmoments.backend.review.entity.Review;
import com.gritmoments.backend.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;

    @Transactional
    public Review createReview(Review review) {
        return reviewRepository.save(review);
    }

    public List<Review> getReviewsByRestaurant(Long restaurantId) {
        return reviewRepository.findByRestaurantId(restaurantId);
    }
}
```

### Step 4: ReviewController 생성

`app/src/main/java/com/gritmoments/backend/review/controller/ReviewController.java`:

```java
package com.gritmoments.backend.review.controller;

import com.gritmoments.backend.review.entity.Review;
import com.gritmoments.backend.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    public Review createReview(@RequestBody Review review) {
        return reviewService.createReview(review);
    }

    @GetMapping("/restaurant/{restaurantId}")
    public List<Review> getReviewsByRestaurant(@PathVariable Long restaurantId) {
        return reviewService.getReviewsByRestaurant(restaurantId);
    }
}
```

### Step 5: 앱 재빌드 및 테스트

```bash
# 앱 재빌드 및 재시작
docker compose up -d --build app

# 리뷰 생성 테스트
curl -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "rating": 5, "content": "맛있어요!"}'

# 가게별 리뷰 조회
curl http://localhost:8080/api/reviews/restaurant/1 | python3 -m json.tool
```

**예상 출력**: 생성한 리뷰 데이터가 반환됩니다.

---

## Level 3: 만들기 -- 모듈형 모놀리스 설계

### 요구사항

이커머스 서비스의 전체 모듈 구조를 설계하세요:

```
도메인 모듈:
- user (회원)
- product (상품)
- order (주문)
- payment (결제)
- delivery (배달)
- review (리뷰)

요구사항:
1. 각 모듈은 독립적인 패키지로 분리
2. 모듈 간 의존 관계를 명확히 정의 (의존 방향 제어)
3. 인터페이스를 통한 느슨한 결합
4. 공통 로직은 common 패키지에 배치
```

### 힌트

모듈 간 의존 관계 예시:
```
user (독립)
  ↑
product (독립)
  ↑
order → user, product
  ↑
payment → order
  ↑
delivery → order, user
  ↑
review → user, product
```

**설계 원칙**:
- 순환 참조 금지 (A -> B -> A는 안 됨)
- 하위 모듈은 상위 모듈을 참조할 수 없음
- 공통 DTO는 별도 패키지로 분리

### 검증

다음 내용을 포함한 `architecture.md` 문서를 작성하세요:

```markdown
# 이커머스 모듈형 모놀리스 설계

## 1. 모듈 구조
- user/
- product/
- order/
- payment/
- delivery/
- review/

## 2. 모듈 간 의존 관계
(다이어그램 또는 텍스트로 표현)

## 3. API 엔드포인트 설계
- POST /api/orders (주문 생성)
- GET /api/orders/{id} (주문 조회)
- ...

## 4. 각 모듈의 책임 분리
- user: 회원가입, 로그인, 프로필 관리
- order: 주문 생성, 조회, 취소
- ...
```

---

## 정리

```bash
# 전체 서비스 종료
docker compose down

# 추가한 코드 삭제 (선택)
rm -rf app/src/main/java/com/gritmoments/backend/review
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| 3계층 구조 | Controller (HTTP) -> Service (비즈니스) -> Repository (DB) |
| Controller | HTTP 요청/응답 처리, Validation |
| Service | 비즈니스 로직, 트랜잭션 관리 |
| Repository | DB CRUD 연산, JPA 쿼리 메서드 |
| 모듈형 모놀리스 | 기능별로 패키지를 분리하여 독립적 모듈로 구성 |
| 느슨한 결합 | 인터페이스를 통한 의존성 주입 (DI) |

## 더 해보기 (선택)

- [ ] DTO(Data Transfer Object) 패턴 적용: Entity와 API 응답 분리
- [ ] 예외 처리: `@ControllerAdvice`로 전역 예외 핸들러 구현
- [ ] Validation: `@Valid`, `@NotNull` 등으로 입력 검증
- [ ] 페이징: `Pageable`을 활용한 대량 데이터 조회 최적화
- [ ] 모듈 간 이벤트 기반 통신: Spring Event 활용
