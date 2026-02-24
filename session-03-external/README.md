# [Session 03] 외부 연동 -- 타임아웃/재시도/서킷브레이커

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- 타임아웃 없이 외부 API를 호출할 때의 위험을 체감한다
- RestTemplate에 타임아웃을 설정할 수 있다
- Spring Retry로 지수 백오프 재시도를 구현할 수 있다
- Resilience4j 서킷 브레이커의 상태 전이를 이해한다

## 사전 준비

**`exercises/` 디렉토리**(이 폴더의 상위)에서 실행:

```bash
cd exercises/
docker compose --profile external up -d
```

실행되는 컨테이너:
- `grit-app` (8080): Spring Boot 애플리케이션
- `grit-mysql` (3306): 데이터베이스
- `grit-redis` (6379): 캐시
- `grit-mock-pg` (9000): 모의 PG 서버

모의 PG 서버 동작:
- **50%**: 즉시 성공 응답
- **30%**: 1~3초 지연 후 성공
- **20%**: 500 에러 반환

---

## Level 1: 따라하기 -- 타임아웃 설정

### Step 1: 모의 PG 서버 확인

```bash
# 헬스체크
curl http://localhost:9000/health

# 결제 요청 (랜덤 지연/실패)
curl -X POST http://localhost:9000/api/payments \
  -H "Content-Type: application/json" \
  -d '{"amount": 25000, "orderId": 1}'
```

여러 번 실행하면 성공/실패/지연이 랜덤하게 나타납니다.

### Step 2: 타임아웃 없이 호출

```bash
# 응답 시간 측정 (최대 3초 지연 가능)
for i in {1..10}; do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" \
    -X POST http://localhost:9000/api/payments \
    -H "Content-Type: application/json" \
    -d '{"amount": 25000}'
done
```

### Step 3: 앱에서 타임아웃 적용 후 비교

앱의 결제 API를 호출하여 RestTemplate 타임아웃 동작을 확인합니다.

---

## Level 2: 변형하기 -- Spring Retry 지수 백오프

재시도 횟수와 간격을 변경하며 성공률 변화를 관찰합니다.

`application.yml`의 Resilience4j retry 설정을 변경:

```yaml
resilience4j:
  retry:
    instances:
      paymentService:
        max-attempts: 5          # 3 -> 5로 변경
        wait-duration: 500ms     # 1s -> 500ms로 변경
```

---

## Level 3: 만들기 -- Resilience4j 서킷 브레이커

### 요구사항

서킷 브레이커의 3가지 상태 전이를 직접 테스트하세요:
1. CLOSED: 정상 상태 (요청 통과)
2. OPEN: 장애 감지 (요청 즉시 실패, 외부 호출 안 함)
3. HALF-OPEN: 일부 요청만 통과하여 복구 확인

---

## 정리

```bash
docker compose --profile external down
```

## 핵심 정리

| 패턴 | 목적 | Spring 기술 |
|------|------|------------|
| 타임아웃 | 무한 대기 방지 | RestTemplate timeout |
| 재시도 | 일시적 실패 복구 | Spring Retry, Resilience4j |
| 서킷브레이커 | 장애 확산 방지 | Resilience4j CircuitBreaker |
| 멱등키 | 이중 처리 방지 | 직접 구현 (DB unique key) |
