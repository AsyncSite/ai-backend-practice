# [Session 06] 고가용성 -- Nginx 로드밸런서와 자동 장애 우회

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Nginx 로드밸런서 + 앱 서버 2대 구성을 할 수 있다
- Round Robin 분배를 직접 확인할 수 있다
- 서버 1대 장애 시 자동 우회(failover)가 동작하는 것을 확인할 수 있다
- 로드밸런싱 알고리즘(least_conn, weighted)을 변경하고 차이를 관찰할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- 프로젝트 루트 디렉토리에서 실행

```bash
# HA 프로파일로 실행: Nginx + App 2대 (app-replica 포함)
docker compose --profile ha up -d
```

실행되는 컨테이너:
- `grit-nginx`: 로드밸런서 (포트 80)
- `grit-app`: 첫 번째 앱 서버 (포트 8080, SERVER_ID=app-1)
- `grit-app-replica`: 두 번째 앱 서버 (포트 8081, SERVER_ID=app-2)
- `grit-mysql`, `grit-redis`: 공유 데이터 레이어

> 포트 충돌이 발생하면 `.env` 파일에서 `APP_PORT` 값을 조정하세요.

```bash
# 컨테이너가 모두 실행 중인지 확인
docker compose ps
```

## 핵심 개념

```
로드밸런싱 없이:
  모든 요청 -> 서버 1대 (장애 시 전체 서비스 중단)

로드밸런싱 있을 때 (Round Robin):
  요청 1 -> Nginx (포트 80) -> grit-app     (app-1)
  요청 2 -> Nginx (포트 80) -> grit-app-replica (app-2)
  요청 3 -> Nginx (포트 80) -> grit-app     (app-1)

  grit-app-replica 장애 -> 모든 요청이 grit-app으로 자동 우회 (High Availability)
```

### nginx.conf 핵심 설정

```nginx
upstream backend {
    server app:8080;          # grit-app (app-1)
    server app-replica:8080;  # grit-app-replica (app-2)
}

server {
    listen 80;
    location / {
        proxy_pass http://backend;
        # 장애 서버를 자동으로 제외하고 다음 서버로 전환
        proxy_next_upstream error timeout http_502 http_503 http_504;
    }
}
```

---

## Level 1: 따라하기 -- 로드밸런싱과 장애 우회 확인

### Step 1: 환경 확인

```bash
# 실행 중인 컨테이너 목록 확인
docker compose ps

# Nginx 헬스체크
curl http://localhost/nginx-health
# 응답: Nginx is healthy

# 앱 헬스체크 (직접)
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

**예상 출력 (docker compose ps)**:
```
NAME                STATUS
grit-app            Up (healthy)
grit-app-replica    Up (healthy)
grit-nginx          Up
grit-mysql          Up (healthy)
grit-redis          Up (healthy)
```

### Step 2: 로드밸런싱 동작 확인 (Round Robin)

```bash
# 10번 요청하여 서버 분배 확인
for i in $(seq 1 10); do
  echo -n "요청 $i: "
  curl -s http://localhost/api/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])"
done
```

**예상 출력**:
```
요청 1: app-1
요청 2: app-2
요청 3: app-1
요청 4: app-2
요청 5: app-1
요청 6: app-2
요청 7: app-1
요청 8: app-2
요청 9: app-1
요청 10: app-2
```

**관찰 포인트**: 요청이 `app-1`과 `app-2` 사이에서 번갈아가며 분배됩니다. 이것이 Round Robin 알고리즘입니다.

### Step 3: Nginx 액세스 로그로 분배 확인

```bash
# 요청 10개를 더 보낸 후 Nginx 로그에서 업스트림 서버 확인
for i in $(seq 1 10); do curl -s http://localhost/api/restaurants > /dev/null; done

docker logs grit-nginx 2>&1 | grep "upstream=" | tail -10
```

로그 형식 예시:
```
172.18.0.1 - - [24/Feb/2026:10:00:01 +0000] "GET /api/restaurants HTTP/1.0" 200 512 upstream=172.18.0.3:8080 response_time=0.012
172.18.0.1 - - [24/Feb/2026:10:00:01 +0000] "GET /api/restaurants HTTP/1.0" 200 512 upstream=172.18.0.4:8080 response_time=0.010
```

두 개의 upstream IP가 번갈아 나타나는 것을 확인할 수 있습니다.

### Step 4: 서버 1대 중지 (장애 시뮬레이션)

```bash
# app-replica 컨테이너 중지 (장애 발생)
docker stop grit-app-replica

# 5번 요청 -> 모든 요청이 살아있는 서버로만 전달됨
for i in $(seq 1 5); do
  echo -n "요청 $i: "
  curl -s http://localhost/api/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])"
done
```

**예상 출력**:
```
요청 1: app-1
요청 2: app-1
요청 3: app-1
요청 4: app-1
요청 5: app-1
```

**관찰 포인트**: Nginx가 자동으로 장애를 감지하고 정상 서버로만 트래픽을 전달합니다(failover). 사용자 입장에서는 서비스가 계속 동작합니다.

### Step 5: Nginx 오류 로그 확인

```bash
# Nginx 오류 로그에서 업스트림 장애 감지 확인
docker logs grit-nginx 2>&1 | grep -i "failed\|error\|upstream" | tail -10
```

Nginx는 `proxy_next_upstream` 설정에 따라 응답하지 않는 서버를 자동으로 건너뜁니다.

### Step 6: 서버 복구

```bash
# app-replica 재시작
docker start grit-app-replica

# 서버 시작 대기 (헬스체크 통과까지 약 30초)
sleep 15

# 앱 준비 확인
curl http://localhost:8081/actuator/health

# 다시 10번 요청 -> 양쪽으로 분배
for i in $(seq 1 10); do
  echo -n "요청 $i: "
  curl -s http://localhost/api/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])"
done
```

**예상 출력**: 다시 `app-1`과 `app-2`가 번갈아 나타나야 합니다.

---

## Level 2: 변형하기 -- 로드밸런싱 알고리즘 변경

### Step 1: least_conn (최소 연결) 알고리즘 적용

`infra/nginx/nginx.conf`에서 upstream 블록을 수정합니다:

```nginx
upstream backend {
    least_conn;               # 이 줄 추가
    server app:8080;
    server app-replica:8080;
}
```

Nginx를 재시작하고 동일한 테스트를 반복합니다:

```bash
docker compose --profile ha restart nginx

# 연속 요청 -> 최소 연결 수를 가진 서버로 우선 분배
for i in $(seq 1 10); do
  echo -n "요청 $i: "
  curl -s http://localhost/api/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])"
done
```

**관찰 포인트**: `least_conn`은 현재 처리 중인 연결 수가 적은 서버에 우선적으로 요청을 보냅니다. 빠른 요청에서는 Round Robin과 차이가 적지만, 처리 시간이 긴 요청(long polling, streaming)이 섞일 때 더 균등한 분배를 합니다.

### Step 2: 가중치(weight) 설정

`infra/nginx/nginx.conf`에서 가중치를 추가합니다:

```nginx
upstream backend {
    server app:8080 weight=3;         # 3배 많은 요청
    server app-replica:8080 weight=1; # 1배
}
```

재시작 후 테스트:

```bash
docker compose --profile ha restart nginx

# 20번 요청하여 비율 확인
APP1=0; APP2=0
for i in $(seq 1 20); do
  SERVER=$(curl -s http://localhost/api/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])")
  echo "요청 $i: $SERVER"
  if [ "$SERVER" = "app-1" ]; then APP1=$((APP1+1)); else APP2=$((APP2+1)); fi
done
echo "app-1: $APP1회, app-2: $APP2회"
```

**예상 출력**:
```
app-1: 15회, app-2: 5회   <- 약 3:1 비율
```

**관찰 포인트**: `app-1`이 약 75% (3/4), `app-2`가 약 25% (1/4)의 요청을 처리합니다. 서버 성능이 다를 때 유용합니다.

### Step 3: 응답 시간 비교

```bash
# 로드밸런서를 통한 요청 (포트 80)
curl -s -o /dev/null -w "Nginx: %{time_total}s\n" http://localhost/api/restaurants

# 단일 서버로 직접 요청 (포트 8080)
curl -s -o /dev/null -w "직접: %{time_total}s\n" http://localhost:8080/api/restaurants
```

**관찰 포인트**: Nginx를 통한 요청에는 프록시 오버헤드가 추가되지만(보통 1~3ms), 장애 우회와 부하 분산이라는 이점을 얻습니다.

### Step 4: max_fails와 fail_timeout으로 장애 감지 민감도 조정

`infra/nginx/nginx.conf`에서 장애 감지 설정을 추가합니다:

```nginx
upstream backend {
    server app:8080 max_fails=2 fail_timeout=10s;
    server app-replica:8080 max_fails=2 fail_timeout=10s;
}
```

- `max_fails=2`: 2번 연속 실패 시 해당 서버를 일시 제외
- `fail_timeout=10s`: 10초 동안 제외 상태 유지

---

## Level 3: 만들기 -- Nginx 설정 직접 완성

### 요구사항

`session-06-availability/level3/nginx-custom.conf` 파일의 TODO를 모두 채워서
Rate Limiting + Weighted 로드밸런싱 + 커스텀 에러 페이지가 포함된 Nginx 설정을 완성하세요.

```
구현할 기능:
1. Rate Limiting: 클라이언트 IP당 초당 10개 요청 제한
2. Weighted 업스트림: app:8080 (weight=3), app-replica:8080 (weight=1)
3. 헬스체크 엔드포인트: /nginx-health
4. 장애 우회 설정: max_fails=3, fail_timeout=30s
5. 커스텀 에러 응답: 502/503/504 시 JSON 형식으로 반환
6. 커스텀 로그 형식: upstream 서버와 응답 시간 포함
```

### 검증

```bash
# 1. 완성한 파일을 nginx.conf에 복사
cp session-06-availability/level3/nginx-custom.conf infra/nginx/nginx.conf

# 2. Nginx 재시작
docker compose --profile ha restart nginx

# 3. 기본 동작 확인
curl http://localhost/api/restaurants

# 4. 서버 1대 중지 후 failover 확인
docker stop grit-app-replica
curl http://localhost/api/restaurants   # 여전히 응답해야 함

# 5. 모든 서버 중지 후 커스텀 에러 확인
docker stop grit-app
curl http://localhost/api/restaurants
# 기대: {"error": "서비스 점검 중입니다. 잠시 후 다시 시도해주세요."}

# 6. 서버 재시작
docker start grit-app grit-app-replica
```

---

## 정리

```bash
# 전체 서비스 종료
docker compose --profile ha down

# 설정 파일을 원래대로 되돌립니다 (git 이용)
# git checkout infra/nginx/nginx.conf
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| 로드밸런싱 | 여러 서버에 요청을 분산하여 부하 분산 + 가용성 향상 |
| Round Robin | 순차적으로 번갈아 요청 분배 (기본 알고리즘, 설정 없으면 자동 적용) |
| least_conn | 현재 연결 수가 적은 서버에 우선 분배 (처리 시간이 다를 때 유리) |
| weight | 서버별 가중치를 설정하여 비율 조정 (성능이 다른 서버 혼용 시) |
| failover | 장애 서버를 자동으로 제외하고 정상 서버로만 트래픽 전달 |
| max_fails | 몇 번 실패 시 서버를 비정상으로 판단할지 임계값 설정 |
| fail_timeout | 비정상 판단 후 몇 초 동안 해당 서버를 제외할지 설정 |

## 더 해보기 (선택)

- [ ] wrk 또는 ab로 부하 테스트: 서버 1대 vs 2대 처리량 비교
- [ ] Nginx access.log 분석: `awk`로 각 서버가 처리한 요청 수 집계
- [ ] `/actuator/health`를 활용한 Nginx passive 헬스체크 강화
- [ ] `ip_hash` 알고리즘: 같은 클라이언트 IP가 항상 같은 서버로 가는 Sticky Session 효과 실험
