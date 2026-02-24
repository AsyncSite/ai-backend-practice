# [Session 11] 컨테이너와 배포 -- Dockerfile과 Docker Compose

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Dockerfile의 각 명령어(FROM, RUN, COPY, ENTRYPOINT)의 역할을 이해한다
- 멀티스테이지 빌드로 이미지 크기를 최적화할 수 있다
- Docker Compose로 멀티 서비스(앱, DB, 캐시)를 실행할 수 있다
- 컨테이너 네트워크와 볼륨의 동작 원리를 이해한다

## 사전 준비

- Docker Desktop 실행 중
- 프로젝트 루트 디렉토리에서 실행:
  ```bash
  docker compose up -d   # MySQL + Redis + App 실행
  ```
- 컨테이너 이름: `grit-app`(앱), `grit-mysql`(DB), `grit-redis`(캐시)
- 앱 기본 포트: `8080` (`.env`에서 `APP_PORT`를 지정한 경우 해당 포트 사용)

## 핵심 개념

```
Dockerfile:
  이미지를 빌드하는 레시피 (소스코드 -> 실행 가능한 컨테이너 이미지)

Docker Compose:
  여러 컨테이너를 YAML 파일로 정의하고 한 번에 실행

멀티스테이지 빌드:
  Stage 1: 빌드 (JDK + Gradle)  ->  app.jar 생성
  Stage 2: 실행 (JRE만)         ->  최종 이미지 (작은 크기)

  빌드 도구(Gradle, JDK)가 최종 이미지에 포함되지 않아
  이미지 크기가 크게 줄어듭니다.
```

---

## Level 1: 따라하기 -- Dockerfile 분석과 이미지 빌드

### Step 1: Dockerfile 분석

```bash
# 실제 Dockerfile 내용 확인
cat app/Dockerfile
```

**실제 내용** (주요 부분):
```dockerfile
# --- Stage 1: 빌드 ---
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# 루트 빌드 파일 복사 (멀티모듈 구조)
COPY build.gradle settings.gradle ./
COPY app/build.gradle app/settings.gradle ./app/

# Gradle 의존성 캐싱 (소스 변경 시에도 재다운로드 방지)
RUN gradle :app:dependencies --no-daemon || true

# 소스 코드 복사 후 JAR 빌드
COPY app/src ./app/src
RUN gradle :app:bootJar --no-daemon -x test

# --- Stage 2: 실행 ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안: root가 아닌 일반 사용자로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 빌드 스테이지에서 JAR 파일만 복사
COPY --from=builder /app/app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

# Spring Boot Actuator 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM 옵션: 컨테이너 메모리에 맞게 자동 조정
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

**명령어 설명**:
- `FROM`: 베이스 이미지 지정
- `WORKDIR`: 작업 디렉토리 설정 (이후 명령의 기준 경로)
- `COPY`: 파일 복사 (호스트 -> 컨테이너, 또는 스테이지 간)
- `RUN`: 빌드 시점에 명령어 실행 (레이어 생성)
- `HEALTHCHECK`: 컨테이너 정상 동작 여부 주기적으로 확인
- `EXPOSE`: 컨테이너가 사용할 포트 선언 (문서화 목적)
- `ENTRYPOINT`: 컨테이너 시작 시 실행할 명령어

### Step 2: 이미지 직접 빌드

```bash
# 멀티스테이지 빌드 (프로젝트 루트에서 실행)
docker build -t backend-practice:v1 -f app/Dockerfile .

# 빌드 진행 과정 확인 (Stage 1 -> Stage 2 순서)
```

**예상 출력**:
```
[+] Building 60.3s (14/14) FINISHED
 => [builder 1/6] FROM gradle:8.5-jdk21
 => [builder 2/6] WORKDIR /app
 => [builder 3/6] COPY build.gradle settings.gradle ./
 => [builder 4/6] COPY app/build.gradle app/settings.gradle ./app/
 => [builder 5/6] RUN gradle :app:dependencies --no-daemon || true
 => [builder 6/6] RUN gradle :app:bootJar --no-daemon -x test
 => [stage-1 1/4] FROM eclipse-temurin:21-jre-alpine
 => [stage-1 2/4] WORKDIR /app
 => [stage-1 3/4] COPY --from=builder /app/app/build/libs/*.jar app.jar
 => exporting to image
```

### Step 3: 이미지 크기 확인

```bash
# 빌드된 이미지 목록 확인
docker images backend-practice

# 베이스 이미지와 크기 비교
docker images | grep -E "backend-practice|eclipse-temurin|gradle"
```

**예상 출력**:
```
REPOSITORY            TAG           SIZE
backend-practice      v1            ~270MB   (JRE + 앱)
gradle                8.5-jdk21     ~500MB   (JDK 전체 포함)
eclipse-temurin       21-jre-alpine ~185MB   (JRE만)
```

**관찰 포인트**: 멀티스테이지 빌드를 사용하면 빌드 도구(Gradle, JDK)가 최종 이미지에 포함되지 않아 이미지 크기가 절반 이하로 줄어듭니다.

### Step 4: 컨테이너 단독 실행 (DB 없이)

```bash
# DB 없이 컨테이너만 실행
docker run --rm -p 8081:8080 --name test-app backend-practice:v1

# 다른 터미널에서 헬스체크 (DB 연결 없으므로 DOWN 상태일 수 있음)
curl http://localhost:8081/actuator/health
```

**예상 결과**: DB 연결이 없으면 `{"status":"DOWN"}` 또는 연결 오류가 발생합니다. 실제 운영에서는 DB와 함께 실행해야 합니다.

Ctrl+C로 컨테이너를 중지합니다.

### Step 5: 이미지 레이어 확인

```bash
# 이미지 히스토리 (각 레이어와 크기 확인)
docker history backend-practice:v1

# 이미지 상세 정보 (메타데이터, 환경변수 등)
docker inspect backend-practice:v1 | python3 -m json.tool | head -80
```

**관찰 포인트**: `RUN` 명령어 하나가 레이어 하나를 만듭니다. 레이어는 캐시되므로 Dockerfile 상단의 변경이 없으면 하단부터만 재빌드됩니다.

---

## Level 2: 변형하기 -- 이미지 최적화와 Docker Compose 이해

### Step 1: 단일 스테이지 vs 멀티스테이지 크기 비교

비교용 단일 스테이지 Dockerfile을 작성합니다(`app/Dockerfile.single`):

```dockerfile
FROM gradle:8.5-jdk21
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY app/build.gradle app/settings.gradle ./app/
COPY app/src ./app/src
RUN gradle :app:bootJar --no-daemon -x test
EXPOSE 8080
CMD ["sh", "-c", "java -jar app/build/libs/*.jar"]
```

빌드하여 크기를 비교합니다:

```bash
# 단일 스테이지 빌드
docker build -t backend-practice:single -f app/Dockerfile.single .

# 크기 비교
docker images | grep backend-practice
```

**예상 결과**:
```
backend-practice   v1        ~270MB   (멀티스테이지 - JRE만)
backend-practice   single    ~500MB   (단일스테이지 - JDK 포함)
```

**관찰 포인트**: 멀티스테이지 빌드가 약 46% 이상 이미지 크기를 절감합니다. 이미지가 작을수록 배포 속도가 빠르고 보안 취약점 노출 면적도 줄어듭니다.

### Step 2: docker-compose.yml 구조 분석

```bash
# docker-compose.yml 전체 내용 확인
cat docker-compose.yml
```

**핵심 구성 요소**:
```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: grit-mysql
    ports:
      - "${MYSQL_PORT:-3306}:3306"   # .env에서 포트 오버라이드 가능
    volumes:
      - mysql_data:/var/lib/mysql    # 데이터 영속성 보장
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", ...]
      interval: 10s

  redis:
    image: redis:7-alpine
    container_name: grit-redis
    ports:
      - "${REDIS_PORT:-6379}:6379"

  app:
    build:
      context: .
      dockerfile: app/Dockerfile
    container_name: grit-app
    ports:
      - "${APP_PORT:-8080}:8080"     # 기본 8080, .env로 변경 가능
    depends_on:
      mysql:
        condition: service_healthy   # MySQL 헬스체크 통과 후 시작
      redis:
        condition: service_healthy

volumes:
  mysql_data:
    name: grit-mysql-data            # 컨테이너 삭제 후에도 데이터 유지

networks:
  backend-net:
    name: grit-backend-net           # 서비스 간 이름으로 통신
```

**관찰 포인트**:
- `depends_on` + `condition: service_healthy`: 단순 시작 순서가 아닌 헬스체크 통과 후 시작 보장
- `${VAR:-default}` 문법: `.env` 파일로 포트를 오버라이드할 수 있음
- `volumes`: 컨테이너 삭제(`docker compose down`) 후에도 데이터 보존
- `networks`: 같은 네트워크 내 서비스는 컨테이너 이름으로 통신 가능

### Step 3: Docker Compose로 전체 서비스 실행

```bash
# 전체 서비스 시작 (이미 실행 중이면 스킵됨)
docker compose up -d

# 실행 중인 서비스와 상태 확인
docker compose ps

# 모든 서비스 로그 실시간 확인
docker compose logs -f
```

Ctrl+C로 로그 모니터링을 종료합니다.

### Step 4: 개별 서비스 로그 확인

```bash
# 앱 서비스만 최근 30줄 확인
docker compose logs app --tail 30

# MySQL 서비스 로그
docker compose logs mysql --tail 20

# Redis 서비스 로그
docker compose logs redis --tail 20

# 앱 로그 실시간 모니터링
docker compose logs -f app
```

### Step 5: 컨테이너 네트워크 확인

```bash
# Docker Compose가 생성한 네트워크 확인
docker network ls | grep grit

# 네트워크 상세 정보 (연결된 컨테이너 목록 확인)
docker network inspect grit-backend-net | python3 -m json.tool
```

**관찰 포인트**: Docker Compose는 `grit-backend-net` 네트워크를 자동으로 생성합니다. 같은 네트워크 내에서는 서비스 이름(`mysql`, `redis`, `app`)으로 서로 통신할 수 있습니다. 앱 컨테이너에서 `mysql:3306`으로 DB에 접근하는 것이 그 예입니다.

---

## Level 3: 만들기 -- GitHub Actions CI/CD 파이프라인

### 요구사항

`.github/workflows/ci.yml` 파일을 작성하여 자동화된 CI/CD 파이프라인을 구현하세요:

```
요구사항:
1. PR 생성 시 자동으로 Gradle 테스트 실행
2. main 브랜치 병합 시 Docker 이미지 빌드
3. 이미지를 GitHub Container Registry(ghcr.io)에 푸시
4. (선택) 배포 서버에 SSH로 접속하여 자동 배포
```

### 힌트

`.github/workflows/ci.yml`:

```yaml
name: CI/CD Pipeline

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run tests
        run: cd app && ./gradlew test

  build-and-push:
    needs: test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: app/Dockerfile
          push: true
          tags: |
            ghcr.io/${{ github.repository }}:latest
            ghcr.io/${{ github.repository }}:${{ github.sha }}
```

### 검증

```bash
# 1. 워크플로우 파일을 커밋하고 푸시
git add .github/workflows/ci.yml
git commit -m "feat: Add CI/CD pipeline"
git push

# 2. GitHub Actions 페이지에서 실행 결과 확인
# https://github.com/{username}/{repo}/actions

# 3. 빌드된 이미지 확인
docker pull ghcr.io/{username}/{repo}:latest
```

**검증 체크리스트**:
- [ ] PR 생성 시 테스트 자동 실행
- [ ] main 브랜치 병합 시 이미지 빌드 및 레지스트리 푸시
- [ ] 테스트 실패 시 빌드가 중단되는가?
- [ ] `latest`와 커밋 SHA 태그가 모두 생성되는가?

---

## 정리

```bash
# 전체 서비스 종료
docker compose down

# 볼륨까지 삭제 (DB 데이터 초기화)
docker compose down -v

# 직접 빌드한 이미지 삭제
docker rmi backend-practice:v1
docker rmi backend-practice:single
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| Dockerfile | 이미지 빌드 레시피 (FROM, COPY, RUN, ENTRYPOINT) |
| 멀티스테이지 빌드 | 빌드 단계와 실행 단계를 분리하여 최종 이미지 크기 최적화 |
| Docker Compose | YAML 파일로 멀티 컨테이너 애플리케이션을 정의하고 실행 |
| `depends_on` + `condition` | 헬스체크 통과 후 다음 서비스를 시작하는 의존성 제어 |
| 네트워크 | 같은 Compose 프로젝트 내 서비스는 이름으로 통신 가능 |
| 볼륨 | 컨테이너 삭제 후에도 데이터 보존 (DB, 로그 등) |
| `HEALTHCHECK` | 컨테이너 내부에서 주기적으로 앱 상태를 확인 |

## 더 해보기 (선택)

- [ ] `.dockerignore` 파일 작성: 빌드 컨텍스트에서 불필요한 파일 제외하여 빌드 속도 향상
- [ ] Docker layer 캐싱 최적화: 변경이 적은 레이어(의존성)를 먼저 배치하여 재빌드 시간 단축
- [ ] `docker-compose.override.yml`: 로컬 개발 환경용 설정을 별도 파일로 분리
- [ ] 환경 변수 관리: `.env.example` 파일 작성 및 실제 `.env`는 `.gitignore`에 추가
- [ ] Watchtower: 컨테이너가 새 이미지로 자동 업데이트되도록 연동
