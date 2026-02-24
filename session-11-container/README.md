# [Session 11] 컨테이너와 배포 -- Dockerfile과 Docker Compose

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Dockerfile의 각 명령어(FROM, RUN, COPY, CMD) 역할을 이해한다
- 멀티스테이지 빌드로 이미지 크기를 최적화할 수 있다
- Docker Compose로 멀티 서비스(앱, DB, 캐시)를 실행할 수 있다
- 컨테이너 네트워크와 볼륨의 동작 원리를 이해한다

## 사전 준비

- Docker Desktop 실행 중
- **exercises/ 디렉토리에서** Docker Compose 실행
- 터미널에서 프로젝트 루트 위치
- 컨테이너 이름: `grit-app`, `grit-mysql`, `grit-redis`

## 핵심 개념

```
Dockerfile:
  이미지를 빌드하는 레시피 (코드 -> 실행 가능한 컨테이너 이미지)

Docker Compose:
  여러 컨테이너를 YAML 파일로 정의하고 한 번에 실행

멀티스테이지 빌드:
  Stage 1: 빌드 (JDK + Gradle)  -> app.jar
  Stage 2: 실행 (JRE만)         -> 최종 이미지 (작은 크기)
```

---

## Level 1: 따라하기 -- Dockerfile 분석과 빌드

### Step 1: Dockerfile 분석

```bash
# 프로젝트 루트로 이동
cd exercises/

# Dockerfile 내용 확인
cat app/Dockerfile
```

**예상 내용**:
```dockerfile
# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

**명령어 설명**:
- `FROM`: 베이스 이미지 지정
- `WORKDIR`: 작업 디렉토리 설정
- `COPY`: 파일 복사 (호스트 -> 컨테이너)
- `RUN`: 빌드 시 명령어 실행
- `EXPOSE`: 컨테이너가 사용할 포트 선언
- `CMD`: 컨테이너 시작 시 실행할 명령어

### Step 2: 이미지 빌드

```bash
# 이미지 빌드 (멀티스테이지)
docker build -t backend-practice:v1 ./app

# 빌드 진행 상황 확인 (Stage 1 -> Stage 2)
```

**예상 출력**:
```
[+] Building 45.2s (12/12) FINISHED
 => [builder 1/4] FROM gradle:8.5-jdk21
 => [builder 2/4] WORKDIR /app
 => [builder 3/4] COPY . .
 => [builder 4/4] RUN gradle bootJar --no-daemon -x test
 => [stage-1 1/3] FROM eclipse-temurin:21-jre-alpine
 => [stage-1 2/3] WORKDIR /app
 => [stage-1 3/3] COPY --from=builder /app/build/libs/*.jar app.jar
 => exporting to image
```

### Step 3: 이미지 크기 확인

```bash
# 빌드된 이미지 목록 확인
docker images backend-practice

# 크기 비교
docker images | grep -E "backend-practice|eclipse-temurin"
```

**예상 출력**:
```
REPOSITORY            TAG       SIZE
backend-practice      v1        250MB   (JRE + 앱)
gradle                8.5-jdk21 450MB   (JDK 전체)
eclipse-temurin       21-jre    180MB   (JRE만)
```

**관찰 포인트**: 멀티스테이지 빌드를 사용하면 빌드 도구(Gradle, JDK)가 최종 이미지에 포함되지 않아 크기가 줄어듭니다.

### Step 4: 컨테이너 단독 실행

```bash
# 컨테이너 실행 (네트워크 연결 없이)
docker run --rm -p 8080:8080 --name test-app backend-practice:v1

# 다른 터미널에서 테스트
curl http://localhost:8080/actuator/health
```

**예상 출력**: DB 연결 없이는 일부 기능만 동작합니다.

Ctrl+C로 컨테이너를 중지합니다.

### Step 5: 이미지 레이어 확인

```bash
# 이미지 히스토리 (각 레이어 확인)
docker history backend-practice:v1

# 상세 정보 확인
docker inspect backend-practice:v1 | python3 -m json.tool
```

---

## Level 2: 변형하기 -- 이미지 최적화와 Docker Compose

### Step 1: 단일 스테이지 vs 멀티스테이지 비교

단일 스테이지 Dockerfile을 작성합니다(`app/Dockerfile.single`):

```dockerfile
FROM gradle:8.5-jdk21
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -x test
EXPOSE 8080
# 주의: exec form(["java",...])에서는 와일드카드(*)가 작동하지 않습니다
# shell form(sh -c)을 사용하거나 명시적 파일명을 지정하세요
CMD ["sh", "-c", "java -jar build/libs/*.jar"]
```

빌드하여 크기를 비교합니다:

```bash
# 단일 스테이지 빌드
docker build -t backend-practice:single -f app/Dockerfile.single ./app

# 크기 비교
docker images | grep backend-practice
```

**예상 결과**:
```
backend-practice   v1        250MB   (멀티스테이지)
backend-practice   single    450MB   (단일스테이지 - JDK 포함)
```

**관찰 포인트**: 멀티스테이지 빌드가 약 44% 크기를 절감합니다.

### Step 2: Docker Compose 파일 분석

```bash
# docker-compose.yml 확인
cat docker-compose.yml
```

**핵심 구성**:
```yaml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    volumes: ["mysql-data:/var/lib/mysql"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  app:
    build: ./app
    ports: ["8080:8080"]
    depends_on: [mysql, redis]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/gritmoments
```

**관찰 포인트**:
- `depends_on`: 서비스 시작 순서 제어
- `environment`: 환경변수로 설정 주입
- `volumes`: 데이터 영속성 보장

### Step 3: Docker Compose로 전체 서비스 실행

```bash
# 전체 서비스 시작
docker compose up -d

# 실행 중인 서비스 확인
docker compose ps

# 로그 확인 (모든 서비스)
docker compose logs -f
```

Ctrl+C로 로그 모니터링을 중지합니다.

### Step 4: 개별 서비스 로그 확인

```bash
# 앱 서비스만 로그 확인
docker compose logs -f app

# MySQL 서비스 로그
docker compose logs mysql --tail 20

# Redis 서비스 로그
docker compose logs redis --tail 20
```

### Step 5: 컨테이너 네트워크 확인

```bash
# Docker Compose가 생성한 네트워크 확인
docker network ls | grep exercises

# 네트워크 상세 정보 (연결된 컨테이너 확인)
docker network inspect grit-backend-net | python3 -m json.tool
```

**관찰 포인트**: Docker Compose는 자동으로 네트워크를 생성하고, 같은 네트워크 내에서 서비스 이름으로 통신할 수 있습니다(`mysql`, `redis` 등).

---

## Level 3: 만들기 -- GitHub Actions CI/CD 파이프라인

### 요구사항

`.github/workflows/ci.yml` 파일을 작성하여 자동화된 CI/CD 파이프라인을 구현하세요:

```
요구사항:
1. PR 생성 시 자동 테스트 실행
2. main 브랜치 병합 시 Docker 이미지 빌드
3. 이미지를 Docker Hub 또는 GitHub Container Registry에 푸시
4. 배포 환경에 자동 배포 (선택)
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
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run tests
        run: cd app && ./gradlew test

  build-and-push:
    needs: test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Docker image
        run: docker build -t myapp:${{ github.sha }} ./app
      - name: Push to registry
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push myapp:${{ github.sha }}
```

### 검증

```bash
# 1. GitHub에 푸시
git add .github/workflows/ci.yml
git commit -m "Add CI/CD pipeline"
git push

# 2. GitHub Actions 페이지에서 실행 결과 확인
# https://github.com/{username}/{repo}/actions

# 3. 빌드된 이미지 확인
docker pull myapp:latest
```

**체크리스트**:
- [ ] PR 시 테스트 자동 실행
- [ ] main 병합 시 이미지 빌드 및 푸시
- [ ] 빌드 실패 시 알림 받기
- [ ] 이미지 태그 전략 (latest, SHA, semver)

---

## 정리

```bash
# 전체 서비스 종료
docker compose down

# 볼륨까지 삭제 (데이터 초기화)
docker compose down -v

# 빌드한 이미지 삭제
docker rmi backend-practice:v1
docker rmi backend-practice:single
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| Dockerfile | 이미지 빌드 레시피 (FROM, COPY, RUN, CMD) |
| 멀티스테이지 빌드 | 빌드 단계와 실행 단계를 분리하여 이미지 크기 최적화 |
| Docker Compose | YAML 파일로 멀티 컨테이너 애플리케이션 정의 및 실행 |
| `depends_on` | 서비스 시작 순서 제어 (완전한 준비 상태는 보장 안 함) |
| 네트워크 | 같은 Compose 프로젝트 내 서비스는 이름으로 통신 가능 |
| 볼륨 | 컨테이너 삭제 후에도 데이터 보존 (DB 데이터 등) |

## 더 해보기 (선택)

- [ ] `.dockerignore` 파일 작성: 불필요한 파일 제외하여 빌드 속도 향상
- [ ] 헬스체크 추가: `HEALTHCHECK` 명령어로 컨테이너 상태 모니터링
- [ ] Docker layer 캐싱 최적화: 변경이 적은 레이어를 먼저 배치
- [ ] docker-compose.override.yml: 로컬 개발 환경용 설정 분리
- [ ] Watchtower: 컨테이너 자동 업데이트 도구 연동
