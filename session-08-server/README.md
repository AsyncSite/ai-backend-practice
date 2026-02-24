# [Session 08] 서버 기초 -- 컨테이너 내부에서 모니터링하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- top, df, free, netstat 등으로 서버 상태를 확인할 수 있다
- 의도적으로 부하를 발생시키고 모니터링으로 감지할 수 있다
- 로그에서 원하는 정보를 grep으로 필터링할 수 있다
- Docker stats로 컨테이너별 리소스 사용량을 추적할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **exercises/ 디렉토리에서** Docker Compose 실행
- 기본 인프라 실행: `docker compose up -d` (MySQL + Redis + App)
- 컨테이너 이름: `grit-app`, `grit-mysql`, `grit-redis`

## 핵심 개념

```
서버 모니터링 4대 요소:

CPU     -> top, docker stats
메모리   -> free, docker stats
디스크   -> df -h
네트워크 -> netstat, ss

로그 분석:
  전체 로그 -> grep 필터링 -> 패턴 발견 -> 문제 진단
```

---

## Level 1: 따라하기 -- 서버 모니터링 명령어

### Step 1: 컨테이너 리소스 확인 (호스트에서)

```bash
# 전체 컨테이너 상태 확인
docker compose ps

# 실시간 리소스 모니터링
docker stats
```

**예상 출력**: 각 컨테이너의 CPU%, MEM%, NET I/O, BLOCK I/O가 실시간으로 표시됩니다.

Ctrl+C로 종료합니다.

### Step 2: 컨테이너 내부 접속

```bash
# 앱 컨테이너에 쉘 접속
docker exec -it grit-app sh
```

이제 컨테이너 내부 쉘에서 작업합니다.

### Step 3: CPU와 메모리 확인

```bash
# CPU/메모리 사용량 상위 프로세스 확인
top -bn1 | head -20

# 메모리 상태 확인 (MB 단위)
free -m

# 시스템 가동 시간과 평균 부하
uptime
```

**예상 출력 (free -m)**:
```
              total        used        free      shared  buff/cache   available
Mem:           2048         512        1024          16         512        1400
Swap:             0           0           0
```

**관찰 포인트**: `available` 컬럼이 실제 사용 가능한 메모리입니다. `used`만 보면 잘못된 판단을 할 수 있습니다.

### Step 4: 디스크 사용량 확인

```bash
# 디스크 사용량 (human-readable)
df -h

# 현재 디렉토리 크기
du -sh /app

# 큰 파일 찾기
du -h /app | sort -h | tail -10
```

**예상 출력 (df -h)**:
```
Filesystem      Size  Used Avail Use% Mounted on
overlay          59G   20G   36G  36% /
```

### Step 5: 네트워크 연결 확인

```bash
# 열린 포트 확인 (LISTEN 상태)
netstat -tlnp 2>/dev/null || ss -tlnp

# 앱이 8080 포트에서 대기 중인지 확인
netstat -tlnp 2>/dev/null | grep 8080
```

**예상 출력**:
```
tcp        0      0 0.0.0.0:8080            0.0.0.0:*               LISTEN      1/java
```

### Step 6: 컨테이너에서 빠져나오기

```bash
# 쉘 종료
exit
```

---

## Level 2: 변형하기 -- 부하 발생과 감지

### Step 1: CPU 부하 발생

```bash
# 터미널 1: CPU 부하 발생 (10초간)
docker exec grit-app sh -c "dd if=/dev/urandom bs=1M count=200 | md5sum" &

# 터미널 2: 실시간 모니터링
docker stats grit-app
```

**예상 출력**: `grit-app`의 CPU%가 급증하는 것을 확인할 수 있습니다.

### Step 2: 메모리 사용량 모니터링

```bash
# 모든 컨테이너 리소스 사용량 (1회 출력)
docker stats --no-stream

# 특정 컨테이너만 모니터링
docker stats grit-app --no-stream
```

**예상 출력**:
```
CONTAINER ID   NAME       CPU %     MEM USAGE / LIMIT     MEM %     NET I/O
abc123         grit-app   5.23%     512MiB / 2GiB        25.00%    1.2kB / 800B
```

### Step 3: 로그 모니터링과 필터링

```bash
# 앱 로그 실시간 모니터링 (새 요청 발생 시)
docker logs -f grit-app
```

다른 터미널에서 요청을 보냅니다:

```bash
# 요청 발생
curl http://localhost:8080/api/restaurants
```

첫 번째 터미널에서 로그가 실시간으로 출력되는 것을 확인합니다. Ctrl+C로 종료합니다.

### Step 4: 로그 필터링

```bash
# ERROR 로그만 필터링
docker logs grit-app 2>&1 | grep -i error

# WARN 로그만 필터링
docker logs grit-app 2>&1 | grep -i warn

# 최근 100줄만 확인
docker logs --tail 100 grit-app

# 특정 시간 이후 로그 확인
docker logs --since 10m grit-app
```

### Step 5: 다중 조건 필터링

```bash
# ERROR 또는 WARN이 포함된 로그
docker logs grit-app 2>&1 | grep -iE "error|warn"

# "restaurant" 키워드가 포함된 로그
docker logs grit-app 2>&1 | grep -i restaurant

# 줄 번호와 함께 출력
docker logs grit-app 2>&1 | grep -in error
```

**관찰 포인트**: 로그 분석은 문제 진단의 첫 번째 단계입니다. grep을 활용하면 수천 줄의 로그에서 핵심 정보를 빠르게 찾을 수 있습니다.

---

## Level 3: 만들기 -- 장애 진단 시나리오

### 요구사항

의도적으로 발생시킨 장애의 원인을 모니터링 도구만으로 찾아내세요:

```
증상:
- 앱 응답 시간이 급격히 느려짐
- 간헐적으로 타임아웃 발생
- 에러 로그는 없음

진단 절차:
1. docker stats로 리소스 사용량 확인
2. docker logs로 애플리케이션 로그 확인
3. docker exec로 컨테이너 내부 진입
4. top, free, netstat로 상세 분석
5. 원인 특정 및 해결 방안 제시
```

### 힌트

가능한 원인들:
- CPU 100% 사용 (무한 루프, 과도한 연산)
- 메모리 부족 (OOM, GC 과부하)
- 디스크 가득 참 (로그 파일 무한 증가)
- 네트워크 연결 문제 (DB 연결 풀 고갈)

### 검증

```bash
# 1. 증상 재현
curl -w "응답시간: %{time_total}s\n" http://localhost:8080/api/restaurants

# 2. 리소스 확인
docker stats --no-stream

# 3. 로그 확인
docker logs --tail 50 grit-app

# 4. 상세 분석
docker exec -it grit-app sh
# 내부에서: top, free -m, df -h, netstat -an | wc -l

# 5. 원인을 Markdown 파일로 작성
# diagnosis.md: 증상 -> 분석 -> 원인 -> 해결방안
```

---

## 정리

```bash
# 전체 서비스 종료
docker compose down

# 컨테이너와 볼륨까지 삭제 (선택)
docker compose down -v
```

## 핵심 정리

| 명령어 | 용도 |
|------|------|
| `docker stats` | 컨테이너별 CPU/메모리/네트워크 실시간 모니터링 |
| `docker logs` | 애플리케이션 로그 확인 (stdout/stderr) |
| `top` | 프로세스별 CPU/메모리 사용량 (컨테이너 내부) |
| `free -m` | 메모리 사용량 (MB 단위) |
| `df -h` | 디스크 사용량 (human-readable) |
| `netstat -tlnp` | 열린 포트와 리스닝 프로세스 확인 |
| `grep -i` | 대소문자 무시 검색 |
| `grep -E` | 정규표현식 검색 (OR 조건 등) |

## 더 해보기 (선택)

- [ ] `htop` 설치 후 더 직관적인 프로세스 모니터링
- [ ] `dmesg`로 시스템 커널 메시지 확인
- [ ] `iostat`, `vmstat`로 I/O 및 가상 메모리 통계 확인
- [ ] 로그 집계 도구(ELK, Grafana Loki) 연동하여 대시보드 구성
- [ ] Prometheus + Grafana로 메트릭 수집 및 시각화
