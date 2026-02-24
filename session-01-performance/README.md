# [Session 01] 성능 -- Redis 캐시로 응답 시간 개선하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Redis 캐시 적용 전/후 응답 시간 차이를 직접 측정할 수 있다
- `@Cacheable` 어노테이션의 동작 원리를 이해한다
- TTL(Time To Live) 변경에 따른 캐시 적중률 변화를 관찰할 수 있다
- cache-aside 패턴을 `RedisTemplate`으로 직접 구현할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **`exercises/` 디렉토리**(이 폴더의 상위)에서 실행:
  ```bash
  cd exercises/   # session-01-performance의 상위 디렉토리
  docker compose up -d   # MySQL + Redis + App 실행
  ```
- 컨테이너 이름: `grit-app`(앱), `grit-mysql`(DB), `grit-redis`(캐시)
- 시드 데이터는 앱 시작 시 자동 삽입됩니다

## 핵심 개념

```
캐시 없이:
  요청 -> DB 조회 (100ms) -> 응답

캐시 있을 때:
  요청 -> Redis 조회 (1ms) -> 응답 (캐시 히트)
  요청 -> Redis 미스 -> DB 조회 (100ms) -> Redis 저장 -> 응답 (캐시 미스)
```

---

## Level 1: 따라하기 -- @Cacheable 전/후 비교

### Step 1: 환경 확인

```bash
# 앱 서버 상태 확인
curl http://localhost:8080/actuator/health

# Redis 연결 확인
docker exec grit-redis redis-cli ping
```

### Step 2: 캐시 없이 메뉴 목록 조회

```bash
# 가게 1번의 메뉴 목록 조회 (첫 번째 요청 - DB 조회)
curl -w "\n응답시간: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus

# 같은 요청을 5번 반복하여 응답 시간 측정
for i in $(seq 1 5); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
done
```

### Step 3: 앱 로그에서 캐시 동작 확인

```bash
# 앱 로그에서 "캐시 미스" 메시지 확인
docker logs grit-app --tail 20 | grep "캐시"
```

첫 번째 요청에서만 `[DB 조회] 가게 1 메뉴 목록 - 캐시 미스(MISS)` 로그가 출력됩니다.
두 번째 요청부터는 Redis에서 바로 반환하므로 이 로그가 나오지 않습니다.

### Step 4: Redis에서 캐시 데이터 확인

```bash
# Redis에 저장된 캐시 키 목록
docker exec grit-redis redis-cli KEYS "grit::menus*"

# 캐시 데이터 확인
docker exec grit-redis redis-cli GET "grit::menus::1"

# TTL(남은 시간) 확인
docker exec grit-redis redis-cli TTL "grit::menus::1"
```

### Step 5: 캐시 삭제 후 재확인

```bash
# 캐시 삭제
docker exec grit-redis redis-cli DEL "grit::menus::1"

# 다시 조회하면 DB에서 가져옴 (캐시 미스)
curl -w "\n응답시간: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
```

---

## Level 2: 변형하기 -- TTL 변경 실험

### Step 1: TTL을 1초로 변경

`app/src/main/resources/application.yml`에서 TTL 변경:

```yaml
spring:
  cache:
    redis:
      time-to-live: 1000  # 1초 (기본: 300000 = 5분)
```

앱을 재시작하고 동일한 테스트를 반복합니다:

```bash
docker compose up -d --build app

# 빠르게 3번 요청 -> 1초 이내이므로 캐시 히트
for i in $(seq 1 3); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
done

# 2초 대기 후 다시 요청 -> 캐시 만료되어 미스
sleep 2
curl -s -o /dev/null -w "2초 후: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
```

### Step 2: TTL을 30초로 변경하여 동일 테스트

### Step 3: 캐시 적중률 관찰

Redis의 INFO stats로 캐시 히트/미스 비율을 확인합니다:

```bash
docker exec grit-redis redis-cli INFO stats | grep keyspace
```

결과 예시:
```
keyspace_hits:42     # 캐시에서 찾은 횟수
keyspace_misses:8    # 캐시에서 못 찾은 횟수
```

캐시 적중률 계산: `hits / (hits + misses) × 100`
- 위 예시: 42 / (42 + 8) × 100 = **84%**

**관찰 포인트**: TTL이 짧으면 데이터 신선도가 높지만 캐시 적중률이 낮아 DB 부하가 증가합니다. TTL이 길면 캐시 적중률은 높지만 오래된 데이터를 보여줄 수 있습니다.

---

## Level 3: 만들기 -- cache-aside 패턴 직접 구현

### 요구사항

`@Cacheable` 어노테이션을 사용하지 않고, `RedisTemplate`을 사용하여 cache-aside 패턴을 직접 구현하세요.

```
cache-aside 패턴:
1. 캐시에서 먼저 조회
2. 캐시 히트 -> 캐시 데이터 반환
3. 캐시 미스 -> DB 조회 -> 캐시 저장 -> 반환
```

### 힌트

`level3/` 폴더에 스캐폴딩 코드가 있습니다. `MenuCacheService.java`의 TODO 부분을 채우세요.

### 검증

```bash
# 첫 요청: DB 조회 + 캐시 저장
curl http://localhost:8080/api/restaurants/1/menus/cached

# 두 번째 요청: 캐시에서 반환 (로그 확인)
curl http://localhost:8080/api/restaurants/1/menus/cached
```

---

## 정리

```bash
# 캐시 전체 삭제
docker exec grit-redis redis-cli FLUSHALL

# 환경 종료
docker compose down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| `@Cacheable` | 메서드 결과를 자동으로 캐시 (같은 파라미터면 DB 조회 생략) |
| TTL | 캐시 만료 시간. 짧으면 신선, 길면 효율적 |
| cache-aside | 앱이 직접 캐시를 관리하는 패턴 (가장 일반적) |
| 캐시 스탬피드 | TTL 만료 시 동시 요청이 모두 DB로 몰리는 현상 |

## 더 해보기 (선택)

- [ ] `@CacheEvict`로 메뉴 변경 시 캐시 무효화 테스트
- [ ] Redis 모니터링: `docker exec grit-redis redis-cli MONITOR`로 실시간 명령어 관찰
- [ ] wrk 또는 ab로 부하 테스트: 캐시 유/무에 따른 처리량 차이 측정
