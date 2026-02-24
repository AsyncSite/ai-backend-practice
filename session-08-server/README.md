# [Session 08] 서버 기초 -- 컨테이너 내부에서 모니터링하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- top, df, free, netstat 등으로 서버 상태를 확인할 수 있다
- 의도적으로 부하를 발생시키고 모니터링으로 감지할 수 있다
- 로그에서 원하는 정보를 grep으로 필터링할 수 있다
- Docker stats로 컨테이너별 리소스 사용량을 추적할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- 프로젝트 루트 디렉토리에서 실행

```bash
docker compose up -d
```

실행되는 컨테이너: `grit-app`, `grit-mysql`, `grit-redis`

```bash
# 앱이 준비될 때까지 대기 (처음 시작 시 30초 내외)
curl http://localhost:8080/actuator/health
# 응답: {"status":"UP"}
```

> 포트가 다를 경우 `.env` 파일의 `APP_PORT` 값을 확인하세요. 기본값은 8080입니다.

## 핵심 개념

```
서버 모니터링 4대 요소:

CPU     -> top, docker stats          어떤 프로세스가 CPU를 쓰는가?
메모리   -> free -m, docker stats     메모리가 부족한가?
디스크   -> df -h, du -sh             디스크가 가득 찼는가?
네트워크 -> netstat -tlnp, ss        어떤 포트가 열려있는가?

로그 분석 흐름:
  전체 로그 -> grep으로 필터링 -> 패턴 발견 -> 문제 진단

Spring Boot 앱 기본 설정 (application.yml):
  server.port: 8080
  server.id: ${SERVER_ID:app-local}   <- 세션 06 로드밸런싱 확인용
  logging.level.root: INFO
  logging.level.com.gritmoments.backend: DEBUG
```

---

## Level 1: 따라하기 -- 서버 모니터링 명령어

### Step 1: 컨테이너 리소스 확인 (호스트에서)

```bash
# 전체 컨테이너 상태 확인
docker compose ps

# 실시간 리소스 모니터링 (Ctrl+C로 종료)
docker stats
```

**예상 출력 (docker stats)**:
```
CONTAINER ID   NAME         CPU %     MEM USAGE / LIMIT   MEM %     NET I/O
a1b2c3d4       grit-app     2.13%     512MiB / 2GiB      25.00%    1.2kB / 800B
e5f6g7h8       grit-mysql   0.50%     128MiB / 2GiB       6.25%    2.1kB / 1.0kB
i9j0k1l2       grit-redis   0.10%     8MiB / 2GiB         0.39%    512B / 256B
```

Ctrl+C로 종료합니다.

### Step 2: 컨테이너 내부 접속

```bash
# 앱 컨테이너에 쉘 접속
docker exec -it grit-app sh
```

이제 컨테이너 내부 쉘에서 작업합니다. 프롬프트가 `#`으로 바뀝니다.

### Step 3: CPU와 메모리 확인

```bash
# CPU/메모리 사용량 상위 프로세스 확인 (1회 출력)
top -bn1 | head -20

# 메모리 상태 확인 (MB 단위)
free -m

# 시스템 가동 시간과 평균 부하
uptime
```

**예상 출력 (free -m)**:
```
              total        used        free      shared  buff/cache   available
Mem:           2048         612        1012          16         424        1300
Swap:             0           0           0
```

**관찰 포인트**: `available` 컬럼이 실제 사용 가능한 메모리입니다.
`used`만 보면 buff/cache(OS가 디스크 캐시로 사용하는 메모리)를 포함하여 과대 계산할 수 있습니다.

**예상 출력 (uptime)**:
```
 10:15:32 up 0:23,  load average: 0.10, 0.15, 0.12
```

`load average`는 1분/5분/15분 평균 부하입니다. CPU 코어 수보다 낮으면 정상입니다.

### Step 4: 디스크 사용량 확인

```bash
# 디스크 사용량 (human-readable)
df -h

# 앱 디렉토리 크기
du -sh /app

# 큰 파일 찾기 (상위 10개)
du -h /app | sort -h | tail -10
```

**예상 출력 (df -h)**:
```
Filesystem      Size  Used Avail Use% Mounted on
overlay          59G   20G   36G  36% /
tmpfs            64M     0   64M   0% /dev
shm              64M     0   64M   0% /dev/shm
```

**관찰 포인트**: `Use%`가 90% 이상이면 위험 신호입니다. 로그 파일이 무한히 쌓이는 경우 디스크가 가득 찰 수 있습니다.

### Step 5: 네트워크 연결 확인

```bash
# 열린 포트 확인 (LISTEN 상태)
netstat -tlnp 2>/dev/null || ss -tlnp

# 앱이 8080 포트에서 대기 중인지 확인
netstat -tlnp 2>/dev/null | grep 8080 || ss -tlnp | grep 8080
```

**예상 출력**:
```
tcp        0      0 0.0.0.0:8080      0.0.0.0:*      LISTEN      1/java
```

### Step 6: 컨테이너에서 빠져나오기

```bash
exit
```

---

## Level 2: 변형하기 -- 부하 발생과 감지

### Step 1: CPU 부하 발생

터미널 2개를 열어 하나에서 부하를 발생시키고, 다른 하나에서 모니터링합니다.

```bash
# 터미널 1: CPU 부하 발생 (약 10초간, 200MB 랜덤 데이터 MD5 계산)
docker exec grit-app sh -c "dd if=/dev/urandom bs=1M count=200 | md5sum" &

# 터미널 2: 실시간 모니터링
docker stats grit-app
```

**예상 출력 (부하 발생 전)**:
```
CONTAINER ID   NAME       CPU %     MEM USAGE / LIMIT
abc123         grit-app   2.13%     512MiB / 2GiB
```

**예상 출력 (부하 발생 중)**:
```
CONTAINER ID   NAME       CPU %     MEM USAGE / LIMIT
abc123         grit-app   98.50%    514MiB / 2GiB   <- CPU 급증!
```

**관찰 포인트**: CPU%가 급증하는 것을 확인할 수 있습니다. 실제 운영에서는 이런 상황에서 알람이 발생해야 합니다.

### Step 2: 메모리 사용량 모니터링

```bash
# 모든 컨테이너 리소스 사용량 (1회 출력, 실시간 갱신 없음)
docker stats --no-stream

# 특정 컨테이너만 모니터링
docker stats grit-app --no-stream
```

**예상 출력**:
```
CONTAINER ID   NAME       CPU %     MEM USAGE / LIMIT     MEM %     NET I/O           BLOCK I/O
abc123         grit-app   5.23%     512MiB / 2GiB        25.00%    1.2kB / 800B      0B / 0B
```

### Step 3: 로그 모니터링과 필터링

터미널 1에서 로그를 실시간으로 확인하면서, 터미널 2에서 요청을 보냅니다.

**터미널 1 (로그 모니터링)**:
```bash
docker logs -f grit-app
```

**터미널 2 (요청 발생)**:
```bash
# 요청 발생
curl http://localhost:8080/api/restaurants

# 여러 번 요청
for i in $(seq 1 5); do
  curl -s http://localhost:8080/api/restaurants/1/menus > /dev/null
  echo "요청 $i 완료"
done
```

터미널 1에서 새 요청이 들어올 때마다 로그가 출력되는 것을 확인합니다. Ctrl+C로 종료합니다.

### Step 4: 로그 필터링

```bash
# ERROR 로그만 필터링
docker logs grit-app 2>&1 | grep -i error

# WARN 로그만 필터링
docker logs grit-app 2>&1 | grep -i warn

# 최근 100줄만 확인
docker logs --tail 100 grit-app

# 최근 10분 이내 로그 확인
docker logs --since 10m grit-app

# 특정 시간 이후 로그
docker logs --since "2026-02-24T10:00:00" grit-app
```

### Step 5: 다중 조건 필터링

```bash
# ERROR 또는 WARN이 포함된 로그 (정규표현식 OR)
docker logs grit-app 2>&1 | grep -iE "error|warn"

# "restaurant" 키워드가 포함된 로그
docker logs grit-app 2>&1 | grep -i restaurant

# 줄 번호와 함께 출력
docker logs grit-app 2>&1 | grep -in "db 조회"

# "DB 조회" 로그가 몇 번 발생했는지 카운트
docker logs grit-app 2>&1 | grep -ic "db 조회"
```

**관찰 포인트**: 로그 분석은 문제 진단의 첫 번째 단계입니다. grep을 활용하면 수천 줄의 로그에서 핵심 정보를 빠르게 찾을 수 있습니다.

### Step 6: 캐시 미스/히트 패턴 분석

세션 01에서 배운 캐시와 연계하여 로그 분석을 실습합니다.

```bash
# 첫 요청 (캐시 미스 발생)
curl http://localhost:8080/api/restaurants/1/menus

# 같은 요청 반복 (캐시 히트)
curl http://localhost:8080/api/restaurants/1/menus

# 캐시 미스 로그가 몇 번 나왔는지 확인
docker logs grit-app 2>&1 | grep "캐시 미스" | wc -l

# 캐시 미스가 발생한 가게 ID 목록
docker logs grit-app 2>&1 | grep "캐시 미스" | grep -oE "가게 [0-9]+"
```

---

## Level 3: 만들기 -- 장애 진단 시나리오

### 요구사항

의도적으로 발생시킨 장애의 원인을 모니터링 도구만으로 찾아내세요.

```
증상:
- 앱 응답 시간이 급격히 느려짐
- 간헐적으로 타임아웃 발생
- 에러 로그는 없음

진단 절차:
1. docker stats로 리소스 사용량 확인 (CPU? 메모리?)
2. docker logs로 애플리케이션 로그 확인 (에러? 경고?)
3. docker exec로 컨테이너 내부 진입
4. top, free, netstat로 상세 분석
5. 원인 특정 및 해결 방안 제시
```

### 장애 시나리오 실습

다음 명령어로 CPU 부하 장애를 재현합니다:

```bash
# CPU 부하 발생 (백그라운드, 약 60초간 지속)
docker exec grit-app sh -c "
  while true; do
    dd if=/dev/urandom bs=1M count=100 | md5sum > /dev/null 2>&1
  done" &

LOAD_PID=$!

# 응답 시간 측정 (느려지는 것 확인)
for i in $(seq 1 5); do
  curl -w "요청 $i 응답시간: %{time_total}s\n" -o /dev/null -s http://localhost:8080/api/restaurants
  sleep 1
done
```

### 진단 흐름

```bash
# 1. 증상 재현 확인
curl -w "응답시간: %{time_total}s\n" -o /dev/null -s http://localhost:8080/api/restaurants

# 2. 리소스 확인
docker stats --no-stream
# CPU%가 90% 이상? -> CPU 병목

# 3. 로그 확인
docker logs --tail 50 grit-app
# 에러 없음? -> 애플리케이션 로직 외 원인

# 4. 컨테이너 내부 상세 분석
docker exec -it grit-app sh

# 내부에서 실행:
top -bn1 | head -15          # 어떤 프로세스가 CPU를 쓰는가?
free -m                       # 메모리는 충분한가?
df -h                         # 디스크는 여유있는가?
netstat -an 2>/dev/null | wc -l  # 네트워크 연결 수는?
exit

# 5. 부하 중지
kill $LOAD_PID 2>/dev/null
# 또는 컨테이너 재시작
# docker compose restart app
```

### 가능한 원인과 진단 방법

| 원인 | docker stats 신호 | 내부 명령어 | 해결 방안 |
|------|------------------|-------------|----------|
| CPU 100% (무한 루프) | CPU% 급증 | `top`에서 CPU 점유 프로세스 확인 | 프로세스 재시작 |
| 메모리 부족 (OOM) | MEM% 90% 이상 | `free -m`에서 available 확인 | 메모리 증설 또는 GC 튜닝 |
| 디스크 가득 참 | BLOCK I/O 급증 | `df -h`에서 100% 확인 | 로그 정리, 디스크 증설 |
| DB 연결 풀 고갈 | CPU% 낮지만 응답 느림 | 로그에서 HikariPool 오류 확인 | `maximum-pool-size` 조정 |

### 검증: 진단 보고서 작성

```bash
# 현재 상태를 파일로 저장
docker stats --no-stream > /tmp/stats.txt
docker logs --tail 100 grit-app > /tmp/app-logs.txt

# diagnosis.md 파일 작성 (형식)
# ## 증상
# ## 분석 과정 (어떤 명령어로 무엇을 확인했는가)
# ## 원인
# ## 해결 방안
```

---

## 정리

```bash
# 전체 서비스 종료
docker compose down

# 컨테이너와 볼륨까지 삭제 (재고/데이터 초기화 포함, 선택)
docker compose down -v
```

## 핵심 정리

| 명령어 | 용도 |
|--------|------|
| `docker stats` | 컨테이너별 CPU/메모리/네트워크 실시간 모니터링 |
| `docker stats --no-stream` | 현재 시점 스냅샷 1회 출력 |
| `docker logs -f` | 애플리케이션 로그 실시간 스트리밍 |
| `docker logs --tail N` | 최근 N줄 로그 확인 |
| `docker logs --since Nm` | 최근 N분 이내 로그 확인 |
| `top -bn1` | 컨테이너 내부 CPU/메모리 상위 프로세스 (1회 출력) |
| `free -m` | 메모리 사용량 (MB 단위, available 컬럼이 핵심) |
| `df -h` | 디스크 사용량 (human-readable, Use%가 90% 이상이면 위험) |
| `netstat -tlnp` | 열린 포트와 리스닝 프로세스 확인 |
| `grep -i` | 대소문자 무시 검색 |
| `grep -E` | 정규표현식 검색 (OR 조건: `error\|warn`) |
| `grep -c` | 매칭되는 줄 수 카운트 |

## 더 해보기 (선택)

- [ ] `htop` 설치 후 더 직관적인 프로세스 모니터링: `docker exec grit-app sh -c "apk add htop && htop"`
- [ ] `dmesg`로 시스템 커널 메시지 확인 (OOM killer 동작 여부)
- [ ] `iostat` / `vmstat`로 I/O 및 가상 메모리 통계 확인
- [ ] Spring Boot Actuator 메트릭 탐색: `curl http://localhost:8080/actuator/metrics`
- [ ] Prometheus + Grafana로 메트릭 수집 및 시각화 (세션 13 미리 보기)
  ```bash
  docker compose --profile monitoring up -d
  # Grafana: http://localhost:3000 (admin/admin)
  ```
