# AI와 함께 배우는 백엔드 실전 가이드 - 실습 환경

## 개요

이 저장소는 "AI와 함께 배우는 백엔드 실전 가이드" 교재의 실습 환경입니다.
Spring Boot 3.x + Java 21 기반의 이커머스(음식 주문) 서비스를 단계별로 구축하면서 백엔드 핵심 역량을 학습합니다.

## 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| 언어 | Java | 21 |
| 프레임워크 | Spring Boot | 3.2.x |
| 빌드 | Gradle | 8.x |
| DB | MySQL | 8.0 |
| 캐시 | Redis | 7 |
| 메시지 브로커 | RabbitMQ | 3 |
| 로드밸런서 | Nginx | 1.25 |
| 모니터링 | Prometheus + Grafana | v2.48 / 10.2 |
| 컨테이너 | Docker + Docker Compose | latest |

## 사전 준비

1. **Docker Desktop** 설치: https://www.docker.com/products/docker-desktop/
2. **Java 21** 설치: https://adoptium.net/
3. **Git** 설치: https://git-scm.com/

## 빠른 시작

```bash
# 1. exercises 디렉토리로 이동
cd exercises/

# 2. 환경 변수 설정
cp .env.example .env

# 3. 기본 인프라 실행 (MySQL + Redis + App)
docker compose up -d

# 4. 상태 확인
docker compose ps

# 5. API 테스트
curl http://localhost:8080/actuator/health
```

## 세션별 실습

각 세션 폴더에 README.md(실습 가이드)와 PROMPTS.md(AI 활용 프롬프트)가 있습니다.

| 세션 | 주제 | 폴더 | Docker 프로필 |
|------|------|------|--------------|
| 01 | 성능/캐시 | `session-01-performance/` | (기본) |
| 02 | 데이터베이스 | `session-02-database/` | (기본) |
| 03 | 외부 연동 | `session-03-external/` | `--profile external` |
| 04 | 비동기 처리 | `session-04-async/` | `--profile async` |
| 05 | 동시성 | `session-05-concurrency/` | (기본) |
| 06 | 고가용성 | `session-06-availability/` | `--profile ha` |
| 07 | 보안 | `session-07-security/` | `--profile security` |
| 08 | 서버 기초 | `session-08-server/` | (기본) |
| 09 | 네트워크 | `session-09-network/` | (기본) |
| 10 | 아키텍처 | `session-10-architecture/` | (기본) |
| 11 | 컨테이너/배포 | `session-11-container/` | (기본) |
| 12 | API 설계 | `session-12-api-design/` | (기본) |
| 13 | 관찰가능성 | `session-13-observability/` | `--profile monitoring` |

## 실습 3단계 체계

모든 세션의 실습은 3단계(Level)로 구성되어 있습니다.

| 레벨 | 이름 | 설명 |
|------|------|------|
| L1 | 따라하기 | 가이드를 그대로 실행하여 결과를 확인합니다 |
| L2 | 변형하기 | 조건이나 파라미터를 바꿔서 차이를 관찰합니다 |
| L3 | 만들기 | 요구사항만 보고 직접 설계하고 구현합니다 |

## 프로젝트 구조

```
exercises/
├── docker-compose.yml          # 전체 인프라 (프로필 기반)
├── .env.example                # 환경 변수 템플릿
├── build.gradle                # 루트 Gradle
├── settings.gradle             # 서브모듈 정의
│
├── app/                        # Spring Boot 메인 애플리케이션
│   ├── Dockerfile
│   ├── build.gradle
│   └── src/main/java/com/gritmoments/backend/
│       ├── BackendApplication.java
│       ├── common/             # 공통 설정, 예외 처리
│       ├── user/               # 회원 모듈
│       ├── restaurant/         # 가게 모듈
│       ├── menu/               # 메뉴 모듈
│       ├── order/              # 주문 모듈
│       └── payment/            # 결제 모듈
│
├── infra/                      # 인프라 설정
│   ├── init-scripts/           # DB 초기화 SQL
│   ├── nginx/                  # Nginx 설정
│   ├── prometheus/             # Prometheus 설정
│   ├── grafana/                # Grafana 대시보드
│   ├── mock-pg/                # 모의 PG API
│   └── vulnerable-app/         # 보안 실습용 취약 앱
│
├── session-01-performance/     # 세션별 실습 자료
├── session-02-database/
├── ...
└── session-13-observability/
```

## 수강생 워크플로우

1. `exercises/` 디렉토리에서 Docker Compose로 인프라 실행
2. 세션 폴더의 README.md를 따라 실습 진행
3. 막히면 PROMPTS.md의 AI 프롬프트 활용
4. 실습 완료 후 다음 세션 폴더로 이동

## 인프라 관리

```bash
# 전체 중지
docker compose down

# 전체 중지 + 데이터 삭제
docker compose down -v

# 특정 프로필만 실행
docker compose --profile monitoring up -d

# 로그 확인
docker compose logs -f app

# 앱만 재빌드
docker compose up -d --build app
```

## 세션 간 프로필 전환

세션마다 다른 Docker Compose 프로필을 사용합니다. 세션을 전환할 때는 이전 프로필을 먼저 종료하세요.

```bash
# 이전 세션 종료
docker compose --profile [이전프로필] down

# 새 세션 시작
docker compose --profile [새프로필] up -d
```

| 세션 | 프로필 | 명령어 |
|------|--------|--------|
| 01, 02, 05, 08-12 | (기본) | `docker compose up -d` |
| 03 (외부 연동) | external | `docker compose --profile external up -d` |
| 04 (비동기) | async | `docker compose --profile async up -d` |
| 06 (고가용성) | ha | `docker compose --profile ha up -d` |
| 07 (보안) | security | `docker compose --profile security up -d` |
| 13 (관찰가능성) | monitoring | `docker compose --profile monitoring up -d` |

## 접속 정보

| 서비스 | URL | 계정 |
|--------|-----|------|
| Spring Boot App | http://localhost:8080 | - |
| MySQL | localhost:3306 | root / root1234 |
| Redis | localhost:6379 | - |
| RabbitMQ UI | http://localhost:15672 | guest / guest |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Mock PG API | http://localhost:9000 | - |
| Vulnerable App | http://localhost:9999 | - |
