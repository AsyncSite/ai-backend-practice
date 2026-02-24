# [Session 12] API 설계 -- RESTful API와 문서화

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- REST API 설계 원칙에 맞게 URL과 응답을 설계할 수 있다
- SpringDoc OpenAPI로 API 문서를 자동 생성할 수 있다
- Spring Data Pageable로 페이지네이션을 구현할 수 있다
- @ControllerAdvice로 에러 응답을 표준화할 수 있다
- Bucket4j로 Rate Limiting을 구현할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **exercises/ 디렉토리에서** Docker Compose 실행
- 기본 인프라 실행: `docker compose up -d` (MySQL + App)
- 컨테이너 이름: `grit-app`, `grit-mysql`

## 핵심 개념

```
RESTful API 설계 원칙:

  리소스 중심 URL:
    GET    /api/restaurants      (목록 조회)
    GET    /api/restaurants/1    (단일 조회)
    POST   /api/restaurants      (생성)
    PUT    /api/restaurants/1    (수정)
    DELETE /api/restaurants/1    (삭제)

  페이지네이션:
    요청: GET /api/restaurants?page=0&size=10&sort=name,asc
    응답: { content: [...], page: 0, size: 10, totalPages: 5 }

  에러 응답 표준화:
    { timestamp, status, error, message, path }

  Rate Limiting:
    요청 -> Bucket4j 검사 -> 허용/거부(429)
```

---

## Level 1: 따라하기 -- API 호출과 응답 분석

### Step 1: 환경 확인

```bash
# 앱 서버 상태 확인
curl http://localhost:8080/actuator/health

# 앱 컨테이너 확인
docker ps | grep grit-app
```

### Step 2: Swagger UI로 API 탐색

브라우저에서 http://localhost:8080/swagger-ui.html 접속

**관찰 포인트**:
- API 엔드포인트 목록
- 요청/응답 스키마 자동 문서화
- Try it out 버튼으로 직접 테스트 가능

### Step 3: curl로 가게 목록 조회 (페이지네이션)

```bash
# 첫 페이지 조회 (size=3)
curl "http://localhost:8080/api/restaurants?page=0&size=3" | python3 -m json.tool
```

**예상 응답 형식**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "맛집1",
      "address": "서울시 강남구",
      "phone": "02-1234-5678"
    },
    ...
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 3
  },
  "totalElements": 10,
  "totalPages": 4,
  "last": false
}
```

### Step 4: 단일 리소스 조회

```bash
# 가게 1번 상세 조회
curl http://localhost:8080/api/restaurants/1 | python3 -m json.tool
```

**예상 응답**:
```json
{
  "id": 1,
  "name": "맛집1",
  "address": "서울시 강남구",
  "phone": "02-1234-5678",
  "createdAt": "2024-01-15T10:30:00"
}
```

### Step 5: 에러 응답 형식 확인

```bash
# 존재하지 않는 리소스 조회 (404 에러)
curl -w "\nHTTP 상태: %{http_code}\n" http://localhost:8080/api/restaurants/99999 | python3 -m json.tool
```

**예상 에러 응답**:
```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Restaurant not found with id: 99999",
  "path": "/api/restaurants/99999"
}
```

**관찰 포인트**: 모든 에러가 `@ControllerAdvice`를 통해 일관된 형식으로 응답됩니다.

### Step 6: 앱 로그에서 요청 처리 확인

```bash
# 앱 로그에서 API 요청 로그 확인
docker logs grit-app --tail 30 | grep "GET /api/restaurants"
```

---

## Level 2: 변형하기 -- 페이지네이션 + Rate Limiting

### Step 1: 페이지네이션 파라미터 실험

```bash
# 두 번째 페이지 조회
curl "http://localhost:8080/api/restaurants?page=1&size=3" | python3 -m json.tool

# 이름순 정렬 (오름차순)
curl "http://localhost:8080/api/restaurants?page=0&size=2&sort=name,asc" | python3 -m json.tool

# 이름순 정렬 (내림차순)
curl "http://localhost:8080/api/restaurants?page=0&size=2&sort=name,desc" | python3 -m json.tool
```

**관찰 포인트**:
- `content` 배열의 정렬 순서 변화
- `pageable.pageNumber`와 실제 요청한 `page` 값 일치 여부
- `last` 필드가 마지막 페이지일 때 `true`로 변경

### Step 2: Rate Limiting 테스트

동일 API를 빠르게 반복 호출하여 429 응답 확인:

```bash
# 10번 연속 빠르게 호출
for i in {1..10}; do
  curl -w "요청 $i: HTTP %{http_code}\n" -s -o /dev/null http://localhost:8080/api/restaurants
done
```

**예상 결과**:
```
요청 1: HTTP 200
요청 2: HTTP 200
요청 3: HTTP 200
요청 4: HTTP 200
요청 5: HTTP 200
요청 6: HTTP 429
요청 7: HTTP 429
...
```

### Step 3: 429 에러 응답 확인

```bash
# Rate Limit 초과 시 응답 확인
curl http://localhost:8080/api/restaurants | python3 -m json.tool
```

**예상 429 응답**:
```json
{
  "timestamp": "2024-01-15T10:35:00.123Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again later.",
  "path": "/api/restaurants"
}
```

### Step 4: Rate Limit 리셋 확인

```bash
# 1분 대기 후 재시도
sleep 60
curl -w "HTTP %{http_code}\n" http://localhost:8080/api/restaurants
```

**관찰 포인트**: Bucket4j가 시간 기반으로 요청 허용량을 리셋합니다.

---

## Level 3: 만들기 -- 상품 검색 API 설계

> **도전 과제**: 이 과제는 챕터에서 다룬 범위를 넘어선 심화 과제입니다. AI를 적극 활용하거나 공식 문서를 참고하세요.

### 요구사항

이커머스 상품 검색 API를 직접 설계하고 구현하세요:

```
엔드포인트: GET /api/products/search

필터링 파라미터:
- category: 카테고리 (예: electronics, clothing)
- minPrice, maxPrice: 가격 범위
- minRating: 최소 평점 (0.0 ~ 5.0)

정렬 파라미터:
- sort: price,asc | price,desc | popularity,desc | createdAt,desc

페이지네이션:
- page, size (오프셋 기반)
- 또는 cursor 기반 페이지네이션 (선택)

응답 형식:
{
  "content": [ 상품 목록 ],
  "filters": { "category": "...", "priceRange": "..." },
  "page": { ... },
  "totalElements": 100
}
```

### 힌트

- `@RequestParam`으로 쿼리 파라미터 받기
- `Pageable`로 페이지네이션 처리
- `@PageableDefault`로 기본값 설정
- `Specification`으로 동적 쿼리 구성 (선택)
- SpringDoc `@Parameter`, `@Operation` 어노테이션으로 API 문서 작성

### 검증

```bash
# 카테고리 필터링
curl "http://localhost:8080/api/products/search?category=electronics&page=0&size=10" | python3 -m json.tool

# 가격 범위 + 평점 필터
curl "http://localhost:8080/api/products/search?minPrice=10000&maxPrice=50000&minRating=4.0" | python3 -m json.tool

# 가격순 정렬
curl "http://localhost:8080/api/products/search?sort=price,asc&page=0&size=5" | python3 -m json.tool

# Swagger UI에서 API 문서 확인
# http://localhost:8080/swagger-ui.html
```

**검증 체크리스트**:
- [ ] 필터링 파라미터가 모두 동작하는가?
- [ ] 정렬이 올바르게 적용되는가?
- [ ] 페이지네이션 응답이 정확한가?
- [ ] Swagger UI에 API 문서가 자동 생성되는가?
- [ ] 에러 응답이 표준 형식을 따르는가?

---

## 정리

```bash
# 환경 종료
docker compose down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| REST 원칙 | 리소스 중심 URL, HTTP 메서드로 행위 표현 |
| 페이지네이션 | `Pageable`로 page, size, sort 처리. 응답에 totalPages, last 포함 |
| SpringDoc | `@Operation`, `@Parameter`로 API 문서 자동 생성 |
| @ControllerAdvice | 전역 예외 처리로 일관된 에러 응답 형식 제공 |
| Rate Limiting | Bucket4j로 분당 요청 수 제한. 초과 시 429 응답 |

## 더 해보기 (선택)

- [ ] HATEOAS: 응답에 관련 링크 포함하여 self-descriptive 만들기
- [ ] 커서 기반 페이지네이션: 대량 데이터에서 오프셋보다 효율적인 방식 구현
- [ ] API 버저닝: `/api/v1/restaurants`, `/api/v2/restaurants`로 버전 관리
- [ ] OpenAPI 스펙 export: Swagger UI에서 JSON/YAML 다운로드하여 API 클라이언트 자동 생성
- [ ] GraphQL vs REST: 동일 기능을 GraphQL로 구현하여 유연성 비교
