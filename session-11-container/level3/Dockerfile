# =============================================================================
# [Session 11 - Level 3] 멀티스테이지 Dockerfile 직접 작성
# =============================================================================
# Spring Boot 애플리케이션을 컨테이너화하는 Dockerfile을 직접 작성합니다.
#
# 멀티스테이지 빌드의 장점:
#   - 빌드 도구(Gradle, JDK)가 최종 이미지에 포함되지 않음
#   - 최종 이미지 크기 대폭 감소 (예: 1.2GB -> 300MB)
#   - 보안 향상 (불필요한 도구가 없으므로 공격 면적 감소)
#
# 이미지 크기 비교:
#   - gradle:8.5-jdk21 (빌드용): ~1.2GB
#   - eclipse-temurin:21-jre-alpine (실행용): ~180MB
#   - 최종 이미지 (앱 포함): ~250MB
# =============================================================================

# =========================================================================
# Stage 1: 빌드 스테이지
# =========================================================================

# TODO 1: 빌드용 베이스 이미지를 지정하세요
#
# Gradle + JDK가 포함된 이미지를 사용합니다.
# AS builder: 이 스테이지에 이름을 붙여서 나중에 참조합니다.
#
# 힌트: FROM gradle:8.5-jdk21 AS builder


# TODO 2: 작업 디렉토리를 설정하세요
#
# 힌트: WORKDIR /app


# TODO 3: Gradle 의존성 캐싱을 위해 빌드 파일만 먼저 복사하세요
#
# Docker 레이어 캐싱 전략:
#   빌드 파일(build.gradle)이 변경되지 않으면 의존성 다운로드를 건너뜁니다.
#   소스 코드만 변경된 경우 이 레이어까지 캐시를 재사용하여 빌드 시간을 단축합니다.
#
# 힌트:
#   COPY build.gradle settings.gradle ./
#   RUN gradle dependencies --no-daemon || true


# TODO 4: 소스 코드를 복사하고 빌드하세요
#
# -x test: 테스트를 건너뜀 (CI/CD에서 별도로 실행)
# --no-daemon: 컨테이너 내에서는 Gradle 데몬이 불필요
#
# 힌트:
#   COPY src ./src
#   RUN gradle bootJar --no-daemon -x test


# =========================================================================
# Stage 2: 실행 스테이지
# =========================================================================

# TODO 5: 실행용 베이스 이미지를 지정하세요
#
# JRE(Java Runtime Environment)만 포함된 경량 이미지를 사용합니다.
# Alpine Linux 기반으로 이미지 크기가 매우 작습니다.
#
# 힌트: FROM eclipse-temurin:21-jre-alpine


# TODO 6: 작업 디렉토리를 설정하세요
#
# 힌트: WORKDIR /app


# TODO 7: 보안을 위해 non-root 사용자를 생성하세요
#
# root 사용자로 컨테이너를 실행하면 보안 위험:
#   - 컨테이너 탈출 시 호스트의 root 권한 획득 가능
#   - 파일 시스템 무제한 접근
#
# 힌트:
#   RUN addgroup -S appgroup && adduser -S appuser -G appgroup


# TODO 8: 빌드 스테이지에서 JAR 파일만 복사하세요
#
# --from=builder: Stage 1(builder)에서 파일을 가져옵니다.
# Gradle 빌드 결과물은 build/libs/ 디렉토리에 생성됩니다.
#
# 힌트: COPY --from=builder /app/build/libs/*.jar app.jar


# TODO 9: 파일 소유권을 설정하고 사용자를 전환하세요
#
# 힌트:
#   RUN chown -R appuser:appgroup /app
#   USER appuser


# TODO 10: 헬스체크를 설정하세요
#
# Docker가 컨테이너의 건강 상태를 주기적으로 확인합니다.
# 건강하지 않으면 컨테이너를 재시작할 수 있습니다.
#
# 설정값:
#   - interval: 30초마다 체크
#   - timeout: 10초 내에 응답 없으면 실패
#   - retries: 3번 연속 실패 시 unhealthy
#   - start-period: 시작 후 40초는 유예 기간 (앱 로딩 시간)
#
# 힌트:
#   HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
#     CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1


# TODO 11: 포트를 노출하세요
#
# EXPOSE는 문서화 목적입니다 (실제 포트 매핑은 docker run -p로)
#
# 힌트: EXPOSE 8080


# TODO 12: 컨테이너 시작 명령어를 설정하세요
#
# JVM 옵션:
#   - UseContainerSupport: 컨테이너의 메모리/CPU 제한을 인식
#   - MaxRAMPercentage=75.0: 컨테이너 메모리의 75%까지 사용
#   - java.security.egd: 난수 생성 속도 개선
#
# 힌트:
#   ENTRYPOINT ["java", \
#     "-XX:+UseContainerSupport", \
#     "-XX:MaxRAMPercentage=75.0", \
#     "-Djava.security.egd=file:/dev/./urandom", \
#     "-jar", "app.jar"]
