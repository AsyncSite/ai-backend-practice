# [Session 10 - Level 1] API 계층 구조 분석

## 3-Layer Architecture

```
┌─────────────────────────────────────┐
│         Controller Layer            │  <- HTTP 요청/응답 처리
│  @RestController, @RequestMapping   │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│          Service Layer              │  <- 비즈니스 로직
│  @Service, @Transactional           │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│        Repository Layer             │  <- 데이터 접근
│  @Repository, JpaRepository         │
└─────────────────────────────────────┘
```

## TODO: 각 계층의 역할 정리

### Controller
- 역할: 
- 책임: 
- 주요 어노테이션: 

### Service
- 역할: 
- 책임: 
- 주요 어노테이션: 

### Repository
- 역할: 
- 책임: 
- 주요 어노테이션: 

## TODO: 프로젝트 구조 분석

```
app/src/main/java/com/gritmoments/backend/
├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── entity/
├── restaurant/
├── menu/
└── order/
```

각 모듈이 어떻게 구성되어 있는지 분석하세요.
