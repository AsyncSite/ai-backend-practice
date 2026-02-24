# =============================================================================
# Spring Boot 멀티스테이지 빌드 Dockerfile
# =============================================================================
# Stage 1: Gradle 빌드 (빌드 도구 + 소스코드 -> JAR 파일 생성)
# Stage 2: 실행 환경 (JRE만 포함 -> 이미지 크기 최소화)
# =============================================================================

# --- Stage 1: 빌드 ---
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# 루트 빌드 파일 복사 (멀티모듈: 플러그인 버전 정의)
COPY build.gradle settings.gradle ./
COPY app/build.gradle app/settings.gradle ./app/

# Gradle 의존성 캐싱
RUN gradle :app:dependencies --no-daemon || true

# 소스 코드 복사 후 빌드
COPY app/src ./app/src
RUN gradle :app:bootJar --no-daemon -x test

# --- Stage 2: 실행 ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안: root가 아닌 일반 사용자로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 빌드 스테이지에서 JAR 파일만 복사
COPY --from=builder /app/app/build/libs/*.jar app.jar

# 소유권 설정
RUN chown -R appuser:appgroup /app

USER appuser

# Spring Boot Actuator 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM 옵션: 컨테이너 환경에 맞게 메모리 설정
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
