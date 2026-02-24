# [Session 13] 관찰가능성 -- Prometheus + Grafana 모니터링

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Prometheus + Grafana 모니터링 스택을 구축할 수 있다
- Spring Boot Actuator가 노출하는 메트릭을 이해한다
- RED 메서드(Rate, Errors, Duration) 기반 대시보드를 활용할 수 있다
- Micrometer로 커스텀 비즈니스 메트릭을 추가할 수 있다
- 알림 규칙을 설정하여 장애를 조기에 감지할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- 프로젝트 루트 디렉토리에서 모니터링 프로파일로 실행:
  ```bash
  docker compose --profile monitoring up -d
  ```
  이 명령어는 기본 서비스(MySQL, Redis, App)에 추가로 모니터링 서비스를 함께 시작합니다.
- 접속 주소:
  - Spring Boot App: http://localhost:8080
  - Prometheus: http://localhost:9090
  - Grafana: http://localhost:3000 (ID: `admin`, PW: `admin`)
- 컨테이너 이름: `grit-app`, `grit-prometheus`, `grit-grafana`
- 앱 포트는 `.env`에서 `APP_PORT`를 설정하면 변경됩니다

## 핵심 개념

```
관찰가능성 3요소 (Observability Pillars):

┌─────────────┐
│ Application │
└──────┬──────┘
       │
       ├──> Metrics ──────────> Prometheus ──> Grafana
       │    (숫자 시계열)        (Pull 방식)    (대시보드)
       │    - 요청 수                          (알림 규칙)
       │    - 응답 시간
       │    - 에러율
       │    - JVM 메모리
       │
       ├──> Logs ────────────> (Loki / ELK)
       │    (이벤트 기록)
       │
       └──> Traces ──────────> (Jaeger / Zipkin)
            (요청 흐름 추적)

이번 실습: Metrics 집중 (Prometheus + Grafana)

RED 메서드:
  Rate     - 초당(또는 분당) 요청 수
  Errors   - 에러율 (5xx / 전체 요청)
  Duration - 응답 시간 (P50, P95, P99)
```

---

## Level 1: 따라하기 -- 기본 메트릭 확인

### Step 1: 환경 확인

```bash
# 모든 서비스 실행 상태 확인
docker compose --profile monitoring ps

# 앱 헬스체크
curl http://localhost:8080/actuator/health

# Prometheus 동작 확인
curl http://localhost:9090/-/healthy

# Grafana 동작 확인
curl http://localhost:3000/api/health
```

### Step 2: Actuator 메트릭 확인

Spring Boot Actuator가 노출하는 엔드포인트를 확인합니다:

```bash
# 헬스체크 (DB, Redis 연결 상태 포함)
curl http://localhost:8080/actuator/health | python3 -m json.tool

# 사용 가능한 메트릭 목록 확인
curl http://localhost:8080/actuator/metrics | python3 -m json.tool

# 특정 메트릭 상세 조회 (JVM 힙 메모리 사용량)
curl http://localhost:8080/actuator/metrics/jvm.memory.used | python3 -m json.tool

# Prometheus 형식으로 전체 메트릭 확인 (Prometheus가 스크래핑하는 형식)
curl http://localhost:8080/actuator/prometheus | head -60
```

**관찰 포인트**: `/actuator/prometheus` 엔드포인트가 노출하는 형식이 Prometheus의 Pull 방식 수집 대상입니다. `infra/prometheus/prometheus.yml`에서 스크래핑 간격(10초)을 설정하고 있습니다.

### Step 3: Prometheus UI에서 쿼리 실행

브라우저에서 http://localhost:9090 접속 후 Graph 탭에서 다음 PromQL 쿼리를 실행합니다.

먼저 트래픽을 생성하여 메트릭이 쌓이도록 합니다:
```bash
for i in $(seq 1 20); do
  curl -s http://localhost:8080/api/restaurants > /dev/null
done
```

**쿼리 1: HTTP 요청 수 (총 누적)**
```promql
http_server_requests_seconds_count
```

**쿼리 2: 분당 요청률 (Rate)**
```promql
rate(http_server_requests_seconds_count[1m])
```

**쿼리 3: 평균 응답 시간 (Duration)**
```promql
rate(http_server_requests_seconds_sum[1m])
  /
rate(http_server_requests_seconds_count[1m])
```

**쿼리 4: JVM 힙 메모리 사용량 (bytes)**
```promql
jvm_memory_used_bytes{area="heap"}
```

**쿼리 5: HTTP 상태 코드별 요청 수**
```promql
http_server_requests_seconds_count{status="200"}
```

### Step 4: Grafana 대시보드 확인

1. http://localhost:3000 접속 (ID: `admin`, PW: `admin`)
2. 첫 로그인 시 비밀번호 변경을 요구하면 "Skip" 가능
3. 좌측 메뉴에서 "Dashboards" 클릭
4. "Backend Practice - RED Dashboard" 선택

**대시보드 패널 확인**:
- Request Rate: 분당 요청 수 시계열 그래프
- Error Rate: 에러 비율 (5xx/4xx)
- Duration P50/P95/P99: 응답 시간 백분위수 분포
- JVM Memory: 힙 메모리 사용량

**관찰 포인트**: Grafana는 `infra/grafana/provisioning/datasources/datasource.yml`에 정의된 Prometheus를 데이터 소스로 자동 연결합니다. 대시보드도 `infra/grafana/dashboards/red-dashboard.json`에서 자동으로 프로비저닝됩니다.

### Step 5: 트래픽 생성하여 대시보드 관찰

```bash
# 가게 목록 조회 100번 (병렬 실행)
for i in $(seq 1 100); do
  curl -s http://localhost:8080/api/restaurants > /dev/null &
done
wait

# 메뉴 조회 50번
for i in $(seq 1 50); do
  curl -s http://localhost:8080/api/restaurants/1/menus > /dev/null &
done
wait

# 에러 응답 생성 (존재하지 않는 가게 조회)
for i in $(seq 1 20); do
  curl -s http://localhost:8080/api/restaurants/99999 > /dev/null &
done
wait
```

**Grafana에서 확인**:
- Request Rate 그래프가 상승하는가?
- Error Rate에서 404 에러가 표시되는가?
- Duration P95/P99 값이 변화하는가?

### Step 6: Prometheus API로 메트릭 쿼리

```bash
# Prometheus HTTP API로 PromQL 실행 (최근 1분간 요청률)
curl -G 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=rate(http_server_requests_seconds_count[1m])' \
  | python3 -m json.tool
```

---

## Level 2: 변형하기 -- 커스텀 메트릭 + 알림 규칙

### Step 1: 커스텀 비즈니스 메트릭 추가

Micrometer를 사용하여 주문 관련 비즈니스 메트릭을 추가합니다.

`app/src/main/java/com/gritmoments/backend/order/metrics/OrderMetrics.java` 생성:

```java
package com.gritmoments.backend.order.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter orderCounter;
    private final Timer orderProcessingTimer;

    public OrderMetrics(MeterRegistry registry) {
        // 주문 생성 수 카운터
        this.orderCounter = Counter.builder("orders.created")
            .description("총 주문 생성 수")
            .tag("service", "order")
            .register(registry);

        // 주문 처리 시간 타이머
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("주문 처리 소요 시간")
            .tag("service", "order")
            .register(registry);
    }

    public void incrementOrderCount() {
        orderCounter.increment();
    }

    public Timer.Sample startProcessing() {
        return Timer.start();
    }

    public void recordProcessingTime(Timer.Sample sample) {
        sample.stop(orderProcessingTimer);
    }
}
```

`OrderService`에서 메트릭 기록:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMetrics metrics;

    @Transactional
    public Order createOrder(OrderRequest request) {
        Timer.Sample sample = metrics.startProcessing();
        try {
            Order order = processOrder(request);
            metrics.incrementOrderCount();
            return order;
        } finally {
            metrics.recordProcessingTime(sample);
        }
    }
}
```

### Step 2: 앱 재시작 및 커스텀 메트릭 확인

```bash
# 앱 재빌드 및 재시작
docker compose --profile monitoring up -d --build app

# 커스텀 메트릭이 노출되는지 확인
curl http://localhost:8080/actuator/prometheus | grep "orders_"
```

**Prometheus UI에서 쿼리**:
```promql
# 주문 생성 속도 (분당)
rate(orders_created_total[1m])

# 주문 처리 시간 평균 (초)
rate(orders_processing_time_seconds_sum[1m])
  /
rate(orders_processing_time_seconds_count[1m])
```

### Step 3: 알림 규칙 확인

`infra/prometheus/alert-rules.yml`에 이미 다음 알림 규칙이 정의되어 있습니다:

```yaml
groups:
  - name: backend-alerts
    rules:
      # 5분간 에러율 5% 초과 시 경고
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count[5m]))
          > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "에러율이 5%를 초과했습니다"

      # p99 응답 시간 3초 초과 시 경고
      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
          ) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "p99 응답 시간이 3초를 초과했습니다"

      # 앱 서버 다운 시 즉시 알림
      - alert: AppServerDown
        expr: up{job=~"spring-boot.*"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "앱 서버가 다운되었습니다"
```

브라우저에서 http://localhost:9090/alerts 접속하여 설정된 알림 규칙을 확인합니다.

### Step 4: 알림 규칙 테스트 (에러 생성)

```bash
# 고의로 404 에러를 대량 발생시키기
for i in $(seq 1 100); do
  curl -s http://localhost:8080/api/restaurants/99999 > /dev/null &
done
wait

# Prometheus Alerts 페이지에서 HighErrorRate 알림 상태 확인
# http://localhost:9090/alerts
# PENDING -> FIRING으로 전환되는 과정을 관찰합니다 (for: 2m 조건)
```

알림 규칙을 추가하거나 임계값을 변경한 경우:

```bash
# Prometheus 설정 재로드 (재시작 없이 반영)
docker compose --profile monitoring restart prometheus
```

---

## Level 3: 만들기 -- 장애 감지와 대응 시뮬레이션

### 요구사항

의도적으로 장애를 발생시키고, 모니터링 시스템으로 감지 -> 진단 -> 해결하는 전체 흐름을 경험하세요.

**시나리오**:
1. 부하 테스트로 응답 시간 증가 유발
2. Grafana 대시보드에서 이상 징후 감지
3. Prometheus 쿼리로 원인 분석 (어떤 엔드포인트? DB 쿼리?)
4. 앱 로그 확인으로 근본 원인 파악
5. 해결 및 지표 정상화 검증

### 힌트

**장애 시나리오 아이디어**:
- DB 커넥션 풀 고갈 시뮬레이션 (동시 요청 급증)
- 존재하지 않는 리소스를 대량 요청하여 에러율 상승

**부하 테스트 도구**:
```bash
# Apache Bench: 100명 동시 사용자, 총 1000개 요청
ab -n 1000 -c 100 http://localhost:8080/api/restaurants

# wrk: 10초간 10개 스레드, 100개 동시 커넥션
wrk -t10 -c100 -d10s http://localhost:8080/api/restaurants
```

**원인 분석용 PromQL**:
```promql
# 엔드포인트별 요청률 (어디서 부하가 몰리는가?)
sum by (uri) (rate(http_server_requests_seconds_count[1m]))

# 엔드포인트별 평균 응답 시간 (어느 API가 느린가?)
sum by (uri) (rate(http_server_requests_seconds_sum[1m]))
  /
sum by (uri) (rate(http_server_requests_seconds_count[1m]))

# DB 커넥션 풀 사용률 (HikariCP)
hikaricp_connections_active / hikaricp_connections_max

# P99 응답 시간 추이
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri)
)
```

### 검증

다음 질문에 답할 수 있어야 합니다:
- [ ] 어느 엔드포인트에서 장애가 발생했는가?
- [ ] 응답 시간이 평소 대비 몇 배 증가했는가?
- [ ] 에러율은 몇 %인가?
- [ ] 근본 원인은 무엇인가? (DB 커넥션 풀? 슬로우 쿼리? 메모리?)
- [ ] 장애 해결 후 메트릭이 정상으로 돌아왔는가?

**검증 스크립트**:
```bash
# 정상 상태 응답 시간 측정
echo "=== 정상 상태 ==="
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants
done

# 부하 발생 (여기서 부하 도구 실행)
echo "=== 부하 발생 중 ==="
ab -n 500 -c 50 http://localhost:8080/api/restaurants 2>&1 | tail -5

# 부하 해소 후 재측정
echo "=== 복구 상태 ==="
sleep 10
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants
done
```

---

## 정리

```bash
# 모니터링 스택 포함 전체 종료
docker compose --profile monitoring down

# 볼륨까지 삭제 (Prometheus/Grafana 데이터 초기화)
docker compose --profile monitoring down -v
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| **Prometheus** | 시계열 메트릭 수집 및 저장. Pull 방식으로 `/actuator/prometheus`를 15초마다 스크래핑 |
| **Grafana** | 메트릭 시각화 대시보드. Prometheus를 데이터 소스로 자동 프로비저닝 |
| **Actuator** | Spring Boot 앱의 메트릭, 헬스체크, 환경 정보를 HTTP 엔드포인트로 노출 |
| **Micrometer** | 벤더 중립적 메트릭 파사드. Prometheus, Datadog, CloudWatch 등 다양한 백엔드 지원 |
| **RED 메서드** | Rate(요청률), Errors(에러율), Duration(응답시간) - 서비스 모니터링 핵심 지표 |
| **알림 규칙** | PromQL 표현식으로 임계값 정의, `for` 조건으로 일시적 스파이크와 지속적 장애를 구분 |
| **PromQL** | Prometheus 쿼리 언어. `rate()`, `histogram_quantile()`, `sum by()` 등 |
| `--profile monitoring` | Docker Compose 프로파일로 모니터링 서비스만 선택적으로 시작 |

## 더 해보기 (선택)

- [ ] Alertmanager 연동하여 Slack 또는 이메일로 알림 전송
- [ ] Loki 추가하여 로그 수집 및 Grafana에서 메트릭-로그 연계 분석
- [ ] JVM 메트릭 심화: GC 시간, GC 횟수, 스레드 수 추적
- [ ] Custom Grafana Dashboard 작성: 비즈니스 메트릭(주문 수, 결제율) 중심 대시보드
- [ ] Prometheus Exporter 추가: MySQL Exporter, Redis Exporter로 인프라 메트릭 수집
- [ ] 분산 추적: Zipkin 또는 Jaeger 연동하여 API -> DB -> 외부 API 전 구간 추적
- [ ] SLO/SLI 정의: 가용성 99.9%, P95 응답시간 < 500ms 목표 설정 및 달성률 모니터링
