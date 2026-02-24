# [Session 06] 고가용성 -- Nginx 로드밸런서와 자동 장애 우회

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- Nginx 로드밸런서 + 앱 서버 2대 구성을 할 수 있다
- Round Robin 분배를 직접 확인할 수 있다
- 서버 1대 장애 시 자동 우회(failover)가 동작하는 것을 확인할 수 있다
- 로드밸런싱 알고리즘을 변경하고 차이를 관찰할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **중요**: `exercises/` 디렉토리(상위 폴더)에서 Docker Compose 실행
  ```bash
  cd exercises/
  docker compose --profile ha up -d
  ```
- 실행되는 컨테이너: `grit-nginx`, `grit-app`, `grit-app-replica`

## 핵심 개념

```
로드밸런싱 없이:
  모든 요청 -> 서버 1대 (장애 시 전체 서비스 중단)

로드밸런싱 있을 때:
  요청 1 -> Nginx -> 서버 A
  요청 2 -> Nginx -> 서버 B (Round Robin)
  요청 3 -> Nginx -> 서버 A

  서버 A 장애 발생 -> 모든 요청 자동으로 서버 B로 전달 (High Availability)
```

---

## Level 1: 따라하기 -- 로드밸런싱과 장애 우회 확인

### Step 1: 환경 확인

```bash
# HA 프로파일로 서버 실행 (Nginx + App 2대)
docker compose --profile ha up -d

# 실행 중인 컨테이너 확인
docker compose ps
```

**예상 출력**: `grit-nginx`, `grit-app`, `grit-app-replica` 3개 컨테이너가 실행 중이어야 합니다.

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
...
```

**관찰 포인트**: 요청이 `app-1`과 `app-2` 사이에서 번갈아가며 분배됩니다(Round Robin 알고리즘).

### Step 3: 서버 1대 중지 (장애 시뮬레이션)

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

**관찰 포인트**: Nginx가 자동으로 장애를 감지하고 정상 서버로만 트래픽을 전달합니다(failover).

### Step 4: Nginx 로그에서 헬스체크 확인

```bash
# Nginx 로그에서 upstream 장애 감지 확인
docker logs grit-nginx 2>&1 | grep -i "failed\|error" | tail -10
```

Nginx는 주기적으로 백엔드 서버를 체크하며, 응답하지 않는 서버를 자동으로 제외합니다.

### Step 5: 서버 복구

```bash
# app-replica 재시작
docker start grit-app-replica

# 서버 시작 대기 (헬스체크 통과까지)
sleep 10

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
    least_conn;  # 이 줄 추가
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

**관찰 포인트**: `least_conn`은 현재 연결 수가 적은 서버에 우선적으로 요청을 보냅니다. 빠른 요청에서는 Round Robin과 차이가 적지만, 긴 요청(long polling, streaming)에서 효과적입니다.

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

for i in $(seq 1 20); do
  echo -n "요청 $i: "
  curl -s http://localhost/api/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])"
done
```

**관찰 포인트**: `app-1`이 약 75% (3/4), `app-2`가 약 25% (1/4)의 요청을 처리합니다. 서버 성능이 다를 때 유용합니다.

### Step 3: 응답 시간 비교

```bash
# 단일 서버로 직접 요청
curl -s -o /dev/null -w "직접: %{time_total}s\n" http://localhost:8080/api/restaurants

# 로드밸런서를 통한 요청
curl -s -o /dev/null -w "Nginx: %{time_total}s\n" http://localhost/api/restaurants
```

**관찰 포인트**: Nginx를 통한 요청에는 프록시 오버헤드가 추가되지만(약 1~5ms), 장애 우회와 부하 분산이라는 이점을 얻습니다.

---

## Level 3: 만들기 -- Blue-Green 배포 시뮬레이션

### 요구사항

Docker Compose와 Nginx 설정을 조합하여 Blue-Green 배포를 구현하세요:

```
Blue-Green 배포:
1. Blue(v1) 환경에 트래픽이 흐름
2. Green(v2) 환경을 백그라운드에서 준비
3. Green 검증 완료 후 트래픽을 Blue -> Green으로 전환
4. Blue 환경 종료
```

### 힌트

- `docker-compose.yml`에 `app-blue`, `app-green` 서비스를 정의합니다
- `nginx.conf`의 upstream 설정을 동적으로 변경하는 스크립트를 작성합니다
- `nginx -s reload`로 무중단 설정 리로드가 가능합니다

### 검증

```bash
# 1. Blue 버전 배포 확인
curl http://localhost/api/server-info

# 2. Green 버전 시작
docker compose up -d app-green

# 3. 트래픽 전환 스크립트 실행
./scripts/switch-to-green.sh

# 4. Green 버전으로 트래픽 전환 확인
curl http://localhost/api/server-info
```

---

## 정리

```bash
# 전체 서비스 종료
docker compose --profile ha down

# 설정 변경한 파일을 원래대로 되돌리세요 (선택)
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| 로드밸런싱 | 여러 서버에 요청을 분산하여 부하 분산 + 가용성 향상 |
| Round Robin | 순차적으로 번갈아 요청 분배 (기본 알고리즘) |
| least_conn | 현재 연결 수가 적은 서버에 우선 분배 |
| weight | 서버별 가중치를 설정하여 비율 조정 |
| failover | 장애 서버를 자동으로 제외하고 정상 서버로만 트래픽 전달 |

## 더 해보기 (선택)

- [ ] `max_fails`와 `fail_timeout` 설정으로 장애 감지 민감도 조정
- [ ] wrk 또는 ab로 부하 테스트: 서버 1대 vs 2대 처리량 비교
- [ ] 헬스체크 엔드포인트(`/actuator/health`) 활용하여 Nginx 업스트림 체크 강화
- [ ] Nginx access.log 분석: 각 서버가 처리한 요청 수 집계
