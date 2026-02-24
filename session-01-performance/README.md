# [Session 01] 성능 -- Redis 캐시로 응답 시간 개선하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Redis 캐시 적용 전/후 응답 시간 차이를 직접 측정할 수 있다
- `@Cacheable` 어노테이션의 동작 원리를 이해한다
- TTL(Time To Live) 변경에 따른 캐시 적중률 변화를 관찰할 수 있다
- cache-aside 패턴을 `RedisTemplate`으로 직접 구현할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **프로젝트 루트 디렉토리**에서 실행:
  ```bash
  # docker-compose.yml이 있는 위치 (ai-backend-practice/)
  docker compose up -d
  ```
- 컨테이너 이름: `grit-app`(앱), `grit-mysql`(DB), `grit-redis`(캐시)
- 시드 데이터는 앱 시작 시 자동 삽입됩니다
- **참고**: 앱 포트는 기본 8080입니다. `.env`에서 `APP_PORT`를 설정하면 바뀔 수 있습니다.

## 핵심 개념

```
캐시 없이:
  요청 -> DB 조회 (100ms) -> 응답

캐시 있을 때:
  요청 -> Redis 조회 (1ms) -> 응답               (캐시 히트)
  요청 -> Redis 미스 -> DB 조회 (100ms) -> Redis 저장 -> 응답  (캐시 미스)
```

코드에서는 `MenuService.getMenusByRestaurant()`에 `@Cacheable(value = "menus", key = "#restaurantId")`가 적용되어 있습니다. 첫 번째 호출에서만 DB를 조회하고, 이후 호출은 Redis에서 직접 반환합니다.

---

## Level 1: 따라하기 -- @Cacheable 전/후 비교

### Step 1: 환경 확인

```bash
# 앱 서버 상태 확인
curl http://localhost:8080/actuator/health
# 기대 출력: {"status":"UP",...}

# Redis 연결 확인
docker exec grit-redis redis-cli ping
# 기대 출력: PONG

# 컨테이너 상태 확인
docker ps | grep grit
# grit-app, grit-mysql, grit-redis 모두 Up 상태여야 합니다
```

### Step 2: 가게 목록 및 메뉴 확인

```bash
# 가게 목록 조회 (시드 데이터에 가게 여러 건이 들어있습니다)
curl http://localhost:8080/api/restaurants
# 기대 출력: {"success":true,"data":{"content":[{"id":1,...},...]}}

# 가게 1번 상세 조회 (RestaurantService에도 @Cacheable 적용됨)
curl http://localhost:8080/api/restaurants/1
# 기대 출력: {"success":true,"data":{"id":1,"name":"...","category":"..."}}
```

### Step 3: 메뉴 목록 조회 -- 첫 번째 요청 (캐시 미스)

```bash
# 가게 1번의 메뉴 목록 조회 (첫 번째 요청 - DB 조회)
curl -w "\n응답시간: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
# 기대 출력 (응답 예시):
# {"success":true,"data":[{"id":1,"name":"된장찌개","price":8000,...},...]}
# 응답시간: 0.12s  <- DB 조회가 발생하여 상대적으로 느림

# 같은 요청을 5번 반복하여 응답 시간 측정
for i in $(seq 1 5); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
done
# 기대 출력:
# 요청 1: 0.118s  <- 캐시 미스 (DB 조회)
# 요청 2: 0.008s  <- 캐시 히트 (Redis 조회)
# 요청 3: 0.007s
# 요청 4: 0.006s
# 요청 5: 0.007s
```

### Step 4: 앱 로그에서 캐시 동작 확인

```bash
# 앱 로그에서 "캐시 미스" 메시지 확인
docker logs grit-app --tail 30 | grep "캐시"
# 기대 출력:
# [DB 조회] 가게 1 메뉴 목록 - 캐시 미스(MISS)
```

첫 번째 요청에서만 `[DB 조회] 가게 1 메뉴 목록 - 캐시 미스(MISS)` 로그가 출력됩니다.
두 번째 요청부터는 Redis에서 바로 반환하므로 이 로그가 나오지 않습니다.

### Step 5: Redis에서 캐시 데이터 직접 확인

```bash
# Redis에 저장된 캐시 키 목록
docker exec grit-redis redis-cli KEYS "menus*"
# 기대 출력: menus::1

# 캐시 데이터 확인 (JSON 직렬화된 메뉴 목록)
docker exec grit-redis redis-cli GET "menus::1"
# 기대 출력: "[{\"id\":1,\"name\":\"된장찌개\",...}]"

# TTL(남은 시간) 확인 - 기본 설정은 5분(300초)
docker exec grit-redis redis-cli TTL "menus::1"
# 기대 출력: 287  <- 300초에서 경과된 시간을 뺀 값
```

### Step 6: 캐시 삭제 후 재확인

```bash
# 캐시 삭제
docker exec grit-redis redis-cli DEL "menus::1"
# 기대 출력: (integer) 1

# 다시 조회하면 DB에서 가져옴 (캐시 미스)
curl -w "\n응답시간: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
# 응답시간이 다시 느려진 것을 확인할 수 있습니다

# 로그 재확인
docker logs grit-app --tail 10 | grep "캐시"
# 기대 출력: [DB 조회] 가게 1 메뉴 목록 - 캐시 미스(MISS)
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
# 프로젝트 루트 디렉토리에서 실행
docker compose up -d --build app

# 빠르게 3번 요청 -> 1초 이내이므로 캐시 히트
for i in $(seq 1 3); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
done
# 기대 출력:
# 요청 1: 0.115s  <- 캐시 미스
# 요청 2: 0.007s  <- 캐시 히트
# 요청 3: 0.006s  <- 캐시 히트

# 2초 대기 후 다시 요청 -> 캐시 만료되어 미스
sleep 2
curl -s -o /dev/null -w "2초 후: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
# 기대 출력:
# 2초 후: 0.113s  <- TTL이 만료되어 다시 DB 조회
```

### Step 2: TTL을 30초로 변경하여 동일 테스트

TTL을 `30000` (30초)으로 설정하고 동일한 테스트를 반복합니다. `sleep 2` 후에도 캐시가 살아있어서 빠르게 응답하는 것을 확인하세요.

```yaml
spring:
  cache:
    redis:
      time-to-live: 30000  # 30초
```

```bash
docker compose up -d --build app

# 1번 요청 후 2초 대기, 다시 요청
curl -s -o /dev/null -w "첫 요청: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
sleep 2
curl -s -o /dev/null -w "2초 후: %{time_total}s\n" http://localhost:8080/api/restaurants/1/menus
# 기대 출력: 2초 후에도 캐시가 유효하므로 0.007s 내외
```

### Step 3: 캐시 적중률 관찰

Redis의 INFO stats로 캐시 히트/미스 비율을 확인합니다:

```bash
docker exec grit-redis redis-cli INFO stats | grep keyspace
# 기대 출력:
# keyspace_hits:42     <- 캐시에서 찾은 횟수
# keyspace_misses:8    <- 캐시에서 못 찾은 횟수
```

캐시 적중률 계산: `hits / (hits + misses) × 100`
- 위 예시: 42 / (42 + 8) × 100 = **84%**

전체 Redis 통계 초기화 후 다시 측정하려면:
```bash
docker exec grit-redis redis-cli CONFIG RESETSTAT
```

**관찰 포인트**: TTL이 짧으면 데이터 신선도가 높지만 캐시 적중률이 낮아 DB 부하가 증가합니다. TTL이 길면 캐시 적중률은 높지만 오래된 데이터를 보여줄 수 있습니다.

---

## Level 3: 만들기 -- cache-aside 패턴 직접 구현

### 요구사항

`@Cacheable` 어노테이션을 사용하지 않고, `RedisTemplate`을 사용하여 cache-aside 패턴을 직접 구현하세요.

```
cache-aside 패턴:
1. 캐시에서 먼저 조회
2. 캐시 히트  -> 캐시 데이터 반환
3. 캐시 미스  -> DB 조회 -> 캐시 저장 -> 반환
```

### 힌트

`level3/MenuCacheService.java`에 스캐폴딩 코드가 있습니다. TODO 1~4 부분을 채우세요.

주요 API:
```java
// Redis 조회
Object cached = redisTemplate.opsForValue().get(cacheKey);

// Redis 저장 (TTL 포함)
redisTemplate.opsForValue().set(cacheKey, jsonString, Duration.ofMinutes(5));

// Redis 삭제
redisTemplate.delete(cacheKey);

// JSON 변환
String json = objectMapper.writeValueAsString(menus);
List<Menu> menus = objectMapper.readValue(json, new TypeReference<List<Menu>>(){});
```

### 검증

`MenuCacheService`를 구현한 뒤 컨트롤러에 연결하거나, 스프링 테스트로 직접 검증할 수 있습니다.

로그로 캐시 미스/히트 동작을 확인하세요:
```bash
docker logs grit-app --tail 20 | grep -E "\[캐시"
# 기대 출력:
# [캐시 미스] 가게 1 메뉴 목록 - DB 조회
# [캐시 히트] 가게 1 메뉴 목록
```

Redis에서 직접 저장된 키를 확인하세요:
```bash
docker exec grit-redis redis-cli KEYS "menus:*"
# 기대 출력: menus:1
```

---

## 정리

```bash
# 캐시 전체 삭제
docker exec grit-redis redis-cli FLUSHALL

# 환경 종료 (프로젝트 루트에서)
docker compose down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| `@Cacheable` | 메서드 결과를 자동으로 캐시. 같은 파라미터면 DB 조회 생략 |
| `@CacheEvict` | 데이터가 변경될 때 캐시를 무효화 (다음 조회 시 DB에서 최신 데이터 로드) |
| TTL | 캐시 만료 시간. 짧으면 신선, 길면 효율적 |
| cache-aside | 앱이 직접 캐시를 관리하는 패턴 (가장 일반적) |
| 캐시 스탬피드 | TTL 만료 시 동시 요청이 모두 DB로 몰리는 현상. 분산 락으로 방지 가능 |

## 더 해보기 (선택)

- [ ] `@CacheEvict`로 메뉴 변경 시 캐시 무효화 테스트
- [ ] Redis 모니터링: `docker exec grit-redis redis-cli MONITOR`로 실시간 명령어 관찰
- [ ] wrk 또는 ab로 부하 테스트: 캐시 유/무에 따른 처리량 차이 측정
- [ ] 가게 상세 조회 `GET /api/restaurants/{id}`에도 `@Cacheable`이 적용되어 있는지 확인하고 동일 실험 반복
