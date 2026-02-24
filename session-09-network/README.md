# [Session 09] 네트워크 기초 -- curl로 HTTP 요청 분석하기

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- `curl -v`로 HTTP 요청/응답 헤더를 분석할 수 있다
- 다양한 HTTP 메서드(GET, POST, PUT, DELETE)와 상태 코드를 이해한다
- `nslookup`/`dig`로 DNS 조회를 할 수 있다
- HTTP 헤더의 역할(Content-Type, Cache-Control, Authorization 등)을 이해한다

## 사전 준비

- Docker Desktop 실행 중
- 프로젝트 루트 디렉토리에서 실행:
  ```bash
  docker compose up -d   # MySQL + Redis + App 실행
  ```
- 컨테이너 이름: `grit-app`(앱), `grit-mysql`(DB), `grit-redis`(캐시)
- 앱 기본 포트: `8080` (`.env`에서 `APP_PORT`를 지정한 경우 해당 포트 사용)

## 핵심 개념

```
HTTP 요청/응답 구조:

요청 (Request):
  GET /api/restaurants HTTP/1.1
  Host: localhost:8080
  User-Agent: curl/7.64.1
  Accept: application/json

응답 (Response):
  HTTP/1.1 200 OK
  Content-Type: application/json
  Content-Length: 1234

  {"data": [...]}

DNS 조회:
  도메인(google.com) -> IP(142.250.196.142)

HTTP 상태 코드:
  2xx - 성공 (200 OK, 201 Created, 204 No Content)
  3xx - 리다이렉션 (301 Moved Permanently, 302 Found)
  4xx - 클라이언트 오류 (400 Bad Request, 401 Unauthorized, 404 Not Found)
  5xx - 서버 오류 (500 Internal Server Error, 503 Service Unavailable)
```

---

## Level 1: 따라하기 -- HTTP 요청/응답 분석

### Step 1: 앱 상태 확인

```bash
# 앱 서버 헬스체크
curl http://localhost:8080/actuator/health

# 컨테이너 실행 상태 확인
docker compose ps
```

**예상 출력**: `{"status":"UP"}` 형태의 JSON 응답이 반환됩니다.

### Step 2: 기본 HTTP 요청

```bash
# 기본 GET 요청 (응답 본문 출력)
curl http://localhost:8080/api/restaurants

# JSON 포맷팅 (python이 있는 경우)
curl http://localhost:8080/api/restaurants | python3 -m json.tool
```

**예상 출력**: JSON 형식의 가게 목록이 출력됩니다.

### Step 3: curl -v로 상세 정보 확인

```bash
# -v 옵션: 요청/응답 헤더를 모두 출력
curl -v http://localhost:8080/api/restaurants
```

**예상 출력**:
```
*   Trying 127.0.0.1:8080...
* Connected to localhost (127.0.0.1) port 8080 (#0)
> GET /api/restaurants HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/7.64.1
> Accept: */*
>
< HTTP/1.1 200
< Content-Type: application/json
< Transfer-Encoding: chunked
< Date: Mon, 03 Feb 2025 10:30:00 GMT
<
{"content": [...]}
```

**출력 해석**:
- `*` (별표): TCP 연결 정보
- `>` (꺾쇠 오른쪽): 내가 보낸 요청 헤더
- `<` (꺾쇠 왼쪽): 서버가 보낸 응답 헤더

### Step 4: 다양한 HTTP 메서드

```bash
# GET: 리소스 조회
curl -X GET http://localhost:8080/api/restaurants/1

# POST: 리소스 생성 (JSON 데이터 전송)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "menuId": 1, "quantity": 2}'

# PUT: 리소스 전체 수정
curl -X PUT http://localhost:8080/api/restaurants/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "새 가게 이름", "address": "서울시 강남구"}'

# DELETE: 리소스 삭제
curl -X DELETE http://localhost:8080/api/restaurants/999
```

### Step 5: HTTP 상태 코드 확인

```bash
# 상태 코드만 출력 (-s: silent, -o: output to /dev/null, -w: write-out)
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/api/restaurants/1

# 여러 요청의 상태 코드 비교
curl -s -o /dev/null -w "정상 요청:    %{http_code}\n" http://localhost:8080/api/restaurants/1
curl -s -o /dev/null -w "없는 리소스:  %{http_code}\n" http://localhost:8080/api/restaurants/99999
curl -s -o /dev/null -w "잘못된 경로:  %{http_code}\n" http://localhost:8080/api/nonexistent
```

**예상 출력**:
```
정상 요청:    200
없는 리소스:  404
잘못된 경로:  404
```

---

## Level 2: 변형하기 -- HTTP 헤더와 DNS 조회

### Step 1: 응답 헤더만 확인

```bash
# -I 옵션: HEAD 메서드로 헤더만 가져오기
curl -I http://localhost:8080/api/restaurants

# GET 메서드로 응답 헤더만 출력 (본문 제외)
curl -v http://localhost:8080/api/restaurants 2>&1 | grep "^< "
```

**예상 출력**:
```
< HTTP/1.1 200
< Content-Type: application/json
< Transfer-Encoding: chunked
< Date: Mon, 03 Feb 2025 10:30:00 GMT
```

### Step 2: 커스텀 헤더 전송

```bash
# Authorization 헤더 추가
curl -H "Authorization: Bearer fake-token-123" \
  http://localhost:8080/api/orders

# 여러 헤더 동시에 추가
curl -H "Content-Type: application/json" \
  -H "X-Request-ID: req-12345" \
  -H "User-Agent: MyApp/1.0" \
  http://localhost:8080/api/restaurants
```

**관찰 포인트**: `-H` 옵션을 여러 번 사용하면 헤더를 여러 개 추가할 수 있습니다.

### Step 3: 응답 시간 측정

```bash
# 응답 시간 포함 출력
curl -o /dev/null -s -w "HTTP %{http_code} / 응답시간: %{time_total}s\n" \
  http://localhost:8080/api/restaurants

# 10번 반복하여 응답 시간 분포 확인
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" http://localhost:8080/api/restaurants
done
```

**관찰 포인트**: 첫 번째 요청이 느린 경우 JVM 워밍업이나 DB 커넥션 초기화 때문입니다. 두 번째 요청부터 안정적인 응답 시간을 보입니다.

### Step 4: DNS 조회

```bash
# nslookup으로 도메인 -> IP 조회
nslookup google.com

# dig로 상세 DNS 조회
dig google.com

# A 레코드만 간단히 조회
dig google.com +short
```

**예상 출력 (nslookup)**:
```
Server:     8.8.8.8
Address:    8.8.8.8#53

Non-authoritative answer:
Name:   google.com
Address: 142.250.196.142
```

### Step 5: 네트워크 왕복 시간(RTT) 확인

```bash
# ping으로 RTT(Round Trip Time) 측정
ping -c 5 google.com

# 로컬 앱 서버 RTT 확인 (로컬이라 거의 0에 가까움)
ping -c 5 localhost
```

**예상 출력**:
```
--- google.com ping statistics ---
5 packets transmitted, 5 received, 0% packet loss, time 4005ms
rtt min/avg/max/mdev = 10.123/12.456/15.789/2.123 ms
```

**관찰 포인트**: 로컬(localhost)은 0.1ms 미만, 국내 서버는 수 ms, 해외 서버는 수십~수백 ms 수준입니다.

---

## Level 3: 만들기 -- HTTP 에코 서버 구현

### 요구사항

간단한 HTTP 에코 컨트롤러를 작성하여 요청을 그대로 돌려보내는 엔드포인트를 구현하세요:

```
기능:
1. GET /echo           -> "Hello, World!" 응답
2. POST /echo          -> 받은 본문(body)을 그대로 응답
3. GET /echo/headers   -> 받은 모든 헤더를 JSON으로 응답
4. 모든 요청 로그 출력 (메서드, 경로, 헤더)
```

### 힌트

`app/src/main/java/com/gritmoments/backend/` 아래에 `echo/controller/EchoController.java` 파일을 생성합니다:

```java
package com.gritmoments.backend.echo.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/echo")
public class EchoController {

    @GetMapping
    public String echoGet() {
        return "Hello, World!";
    }

    @PostMapping
    public String echoPost(@RequestBody String body) {
        return body;
    }

    @GetMapping("/headers")
    public Map<String, String> echoHeaders(@RequestHeader Map<String, String> headers) {
        return headers;
    }
}
```

### 검증

앱을 재빌드한 뒤 다음 명령어로 동작을 확인합니다:

```bash
# 앱 재빌드 및 재시작
docker compose up -d --build app

# GET 요청 -> "Hello, World!"
curl http://localhost:8080/echo

# POST 요청 -> 보낸 본문이 그대로 반환
curl -X POST http://localhost:8080/echo \
  -H "Content-Type: text/plain" \
  -d "This is my message"

# 헤더 에코 확인
curl http://localhost:8080/echo/headers | python3 -m json.tool

# 커스텀 헤더가 응답에 포함되는지 확인
curl -H "X-Custom-Header: MyValue" http://localhost:8080/echo/headers
```

**검증 체크리스트**:
- [ ] GET `/echo`가 "Hello, World!"를 반환하는가?
- [ ] POST `/echo`에 전송한 본문이 그대로 반환되는가?
- [ ] GET `/echo/headers`에서 내가 보낸 `X-Custom-Header`가 포함되는가?

---

## 정리

```bash
# 앱 서버 종료
docker compose down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| `curl -v` | HTTP 요청/응답 헤더를 모두 출력 (디버깅용) |
| `curl -I` | HEAD 메서드로 응답 헤더만 가져오기 (본문 없음) |
| `curl -X` | HTTP 메서드 지정 (GET, POST, PUT, DELETE) |
| `curl -H` | 커스텀 헤더 전송 (여러 번 사용 가능) |
| `curl -d` | POST/PUT 본문 데이터 전송 |
| `curl -w` | 응답 시간, 상태 코드 등 메타 정보 출력 |
| DNS | 도메인 이름을 IP 주소로 변환하는 시스템 |
| RTT | 요청이 서버까지 왕복하는 시간 (네트워크 지연 지표) |
| `nslookup` / `dig` | DNS 레코드 조회 도구 |

## 더 해보기 (선택)

- [ ] `curl -L`로 리다이렉션 자동 추적
- [ ] `curl --compressed`로 gzip 압축 응답 자동 해제
- [ ] `curl --cookie` / `--cookie-jar`로 쿠키 관리
- [ ] `tcpdump`나 Wireshark로 패킷 수준 네트워크 분석
- [ ] HTTP/2 요청 테스트 (`curl --http2`)
