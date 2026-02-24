# [Session 07] 보안 -- SQL Injection 공격과 방어

## 목표

이 실습을 마치면 다음을 할 수 있습니다:
- SQL Injection 공격을 직접 재현하고 위험성을 체감한다
- 파라미터 바인딩으로 SQL Injection을 방어할 수 있다
- XSS 공격을 이해하고 이스케이프 처리로 방어할 수 있다
- BCryptPasswordEncoder로 비밀번호를 안전하게 해싱할 수 있다
- Spring Security + JWT를 사용한 인증/인가 시스템을 구현할 수 있다

## 사전 준비

- Docker Desktop 실행 중
- **exercises/ 디렉토리에서** Docker Compose 실행
- 컨테이너 이름: `grit-app`, `grit-mysql`, `grit-vulnerable-app`

```bash
# exercises/ 디렉토리에서 실행
# 취약한 앱 + MySQL 실행
docker compose --profile security up -d

# 취약한 앱 접속 확인
curl http://localhost:9999

# MySQL 연결 확인
docker exec grit-mysql mysql -uroot -proot1234 -e "SELECT COUNT(*) FROM backend_study.users"
```

실행 환경:
- 취약한 앱: http://localhost:9999 (Node.js Express)
- MySQL: localhost:3306
- Spring Boot 앱: localhost:8080

> Spring Boot는 기본적으로 JPA의 Prepared Statement를 사용하여 SQL Injection에 안전합니다. SQL Injection 공격을 직접 재현하기 위해, 의도적으로 취약하게 만든 별도의 Node.js 앱을 사용합니다.

## 핵심 개념

```
SQL Injection 공격 흐름:

1. 정상 요청:
   사용자 입력: "Alice"
   생성된 쿼리: SELECT * FROM users WHERE name LIKE '%Alice%'
   결과: Alice 사용자만 조회 ✅

2. SQL Injection 공격:
   사용자 입력: ' OR '1'='1
   생성된 쿼리: SELECT * FROM users WHERE name LIKE '%' OR '1'='1%'
                                                    ↑ 항상 참
   결과: 모든 사용자 정보 노출 ❌

3. 방어 (Prepared Statement):
   사용자 입력: ' OR '1'='1
   생성된 쿼리: SELECT * FROM users WHERE name LIKE ?
   파라미터: ["%' OR '1'='1%"]
   결과: 문자열로 처리되어 공격 차단 ✅


XSS (Cross-Site Scripting) 공격 흐름:

1. 공격자 입력:
   <script>alert(document.cookie)</script>

2. 취약한 서버 (이스케이프 없음):
   HTML: <div>안녕하세요, <script>alert(document.cookie)</script>님!</div>
   결과: 스크립트 실행 → 쿠키 탈취 ❌

3. 안전한 서버 (HTML 이스케이프):
   HTML: <div>안녕하세요, &lt;script&gt;alert(document.cookie)&lt;/script&gt;님!</div>
   결과: 텍스트로만 표시 ✅


비밀번호 해싱 (BCrypt):

평문 저장 (절대 금지):
  DB: password = "test123"  ❌ 해킹 시 그대로 노출

단순 해시 (MD5/SHA-1):
  DB: password = "cc03e747a6afbbcbf8be7668acfebee5"  ❌ 레인보우 테이블 공격 가능

BCrypt 해시 (권장):
  DB: password = "$2a$10$N9qo8uLOickgx2ZMRZoMye..."  ✅ 솔트 자동 생성
  같은 비밀번호도 매번 다른 해시값 생성
```

---

## Level 1: 따라하기 -- SQL Injection 재현

### Step 1: 브라우저에서 취약한 앱 접속

```bash
# 브라우저에서 접속
open http://localhost:9999
```

"보안 취약점 학습 실습 환경" 페이지가 표시됩니다.

### Step 2: SQL Injection 공격 실행 (curl)

```bash
# 정상 검색: "Alice" 검색
curl "http://localhost:9999/search-vulnerable?q=Alice"

# 결과: Alice 사용자 1명만 조회됨
# ID | Name  | Email
# 1  | Alice | alice@example.com


# SQL Injection 공격: 모든 사용자 정보 노출
curl "http://localhost:9999/search-vulnerable?q=%27%20OR%20%271%27%3D%271"
# URL 디코딩: ' OR '1'='1

# 결과: 모든 사용자 정보 노출!
# 실행된 쿼리: SELECT id, name, email FROM users WHERE name LIKE '%' OR '1'='1%'
# ID | Name  | Email
# 1  | Alice | alice@example.com
# 2  | Bob   | bob@example.com
# 3  | Carol | carol@example.com
# ... (모든 사용자)


# UNION 기반 SQL Injection: 다른 테이블 데이터 탈취
curl "http://localhost:9999/search-vulnerable?q=%27%20UNION%20SELECT%20id%2C%20name%2C%20email%20FROM%20users--"
# URL 디코딩: ' UNION SELECT id, name, email FROM users--

# 결과: users 테이블의 모든 데이터 조회
```

### Step 3: 안전한 버전과 비교

```bash
# 같은 공격을 안전한 엔드포인트에 시도
curl "http://localhost:9999/search-safe?q=%27%20OR%20%271%27%3D%271"

# 결과: 검색 결과가 없습니다.
# 이유: ' OR '1'='1 문자열 자체를 검색했기 때문 (SQL 코드로 해석되지 않음)
```

### Step 4: XSS 공격 재현

```bash
# XSS 공격 (취약한 버전)
curl -X POST http://localhost:9999/greet-vulnerable \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=<script>alert('XSS')</script>"

# 결과: 응답 HTML에 스크립트가 그대로 포함됨
# <div class="greeting">
#   안녕하세요, <script>alert('XSS')</script>님!
# </div>
# 브라우저에서 실행 시 alert 창이 뜸 ❌


# XSS 방어 (안전한 버전)
curl -X POST http://localhost:9999/greet-safe \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=<script>alert('XSS')</script>"

# 결과: HTML 이스케이프 처리됨
# <div class="greeting">
#   안녕하세요, &lt;script&gt;alert('XSS')&lt;/script&gt;님!
# </div>
# 텍스트로만 표시됨 ✅
```

### Step 5: 쉘 스크립트로 자동화 테스트

```bash
# level1 디렉토리의 테스트 스크립트 실행
cd exercises/session-07-security/level1
chmod +x sql-injection-test.sh
./sql-injection-test.sh

# 스크립트는 다양한 SQL Injection 공격 패턴을 자동으로 테스트합니다
```

---

## Level 2: 변형하기 -- BCrypt 해싱 + XSS 방어

### Step 1: BCrypt 해싱 동작 확인

Spring Security의 BCryptPasswordEncoder는 같은 비밀번호라도 매번 다른 해시값을 생성합니다.

```bash
# 같은 비밀번호로 두 번 해싱 → 매번 다른 해시값
curl "http://localhost:8080/api/auth/hash?password=test123"
# 결과: $2a$10$N9qo8uLOickgx2ZMRZoMye...

curl "http://localhost:8080/api/auth/hash?password=test123"
# 결과: $2a$10$Xj3kL8mQp9rTvNwYhS2Poe...  (다른 값!)

# 이유: BCrypt는 자동으로 랜덤 솔트(salt)를 생성하여 해시값에 포함
```

### Step 2: BCrypt 해시 구조 이해

```
BCrypt 해시 구조:
$2a$10$N9qo8uLOickgx2ZMRZoMyeIX1HbYa9H3W6Q4L5Z6P.QW8Xz9Y1234

$2a       - BCrypt 알고리즘 버전
$10       - Cost Factor (2^10 = 1024 라운드 반복, 느릴수록 안전)
$N9qo...  - 솔트 (22글자, Base64 인코딩)
IX1Hb...  - 실제 해시값 (31글자)
```

### Step 3: BCrypt 검증 테스트

```bash
# 비밀번호 해싱
HASH=$(curl -s "http://localhost:8080/api/auth/hash?password=test123")
echo "해시값: $HASH"

# 검증 성공
curl -X POST "http://localhost:8080/api/auth/verify" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"test123\",\"hash\":\"$HASH\"}"
# 결과: {"valid": true}

# 검증 실패
curl -X POST "http://localhost:8080/api/auth/verify" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"wrong\",\"hash\":\"$HASH\"}"
# 결과: {"valid": false}
```

### Step 4: 쉘 스크립트로 BCrypt 테스트

```bash
# level2 디렉토리의 테스트 스크립트 실행
cd exercises/session-07-security/level2
chmod +x bcrypt-test.sh
./bcrypt-test.sh

# 스크립트는 BCrypt 해싱과 검증을 자동으로 테스트합니다
```

### Step 5: XSS 방어 심화 - CSP 헤더 추가

Content Security Policy (CSP) 헤더로 XSS를 추가 방어할 수 있습니다.

Spring Security에서 CSP 헤더 추가 예시:
```java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("script-src 'self'; object-src 'none';")
    )
);
```

이 설정은 자체 도메인의 스크립트만 실행하고, 인라인 스크립트를 차단합니다.

---

## Level 3: 만들기 -- JWT 인증 시스템

> **도전 과제**: 이 과제는 챕터에서 다룬 범위를 넘어선 심화 과제입니다. AI를 적극 활용하거나 공식 문서를 참고하세요.

### 요구사항

Spring Security + JWT를 사용한 완전한 인증/인가 시스템을 구현하세요:

1. **회원가입 API** (POST /api/auth/signup)
   - 요청: `{"username": "user1", "password": "pass123", "role": "CUSTOMER"}`
   - BCrypt로 비밀번호 해싱 후 DB 저장
   - 응답: 사용자 정보 (비밀번호 제외)

2. **로그인 API** (POST /api/auth/login)
   - 요청: `{"username": "user1", "password": "pass123"}`
   - 비밀번호 검증 (BCrypt matches)
   - JWT 토큰 생성 및 반환
   - 응답: `{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}`

3. **JWT 토큰 구조**
   ```
   Header (알고리즘 정보):
     {"alg": "HS256", "typ": "JWT"}

   Payload (사용자 정보):
     {"sub": "user1", "role": "CUSTOMER", "exp": 1234567890}

   Signature (서명):
     HMACSHA256(base64(header) + "." + base64(payload), secret)
   ```

4. **인증 필터** (JwtAuthenticationFilter)
   - 모든 요청에서 Authorization 헤더 확인
   - Bearer 토큰 추출 및 검증
   - 유효한 토큰이면 SecurityContext에 인증 정보 저장
   - 유효하지 않으면 401 Unauthorized 응답

5. **역할 기반 접근 제어**
   - CUSTOMER: 자신의 주문만 조회 가능
   - OWNER: 자신의 가게 메뉴/주문 관리 가능
   - ADMIN: 모든 데이터 접근 가능

### 힌트

`level3/` 폴더에 스캐폴딩 코드가 있습니다:
- `SecurityConfig.java`: Spring Security 설정 (TODO 7개)
- `JwtTokenProvider.java`: JWT 토큰 생성/검증 (별도 구현 필요)
- `JwtAuthenticationFilter.java`: JWT 인증 필터 (별도 구현 필요)
- `AuthController.java`: 회원가입/로그인 API (별도 구현 필요)

### 구현 순서

1. **SecurityConfig.java 완성**
   - TODO 1-7을 모두 채우세요
   - BCryptPasswordEncoder Bean 등록
   - CORS, CSRF, 세션 관리 설정

2. **JwtTokenProvider 구현**
   - 토큰 생성: `generateToken(username, role)`
   - 토큰 검증: `validateToken(token)`
   - 토큰에서 정보 추출: `getUsername(token)`, `getRole(token)`

3. **JwtAuthenticationFilter 구현**
   - Authorization 헤더에서 Bearer 토큰 추출
   - JwtTokenProvider로 토큰 검증
   - 유효하면 SecurityContext에 인증 정보 설정

4. **AuthController 구현**
   - `/api/auth/signup`: 회원가입
   - `/api/auth/login`: 로그인 (JWT 반환)

### 검증

```bash
# 1. 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"customer1","password":"pass123","role":"CUSTOMER"}'

# 응답: {"id":1,"username":"customer1","role":"CUSTOMER"}


# 2. 로그인
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"customer1","password":"pass123"}' \
  | jq -r '.token')

echo "JWT Token: $TOKEN"


# 3. 인증 없이 보호된 API 접근 시도 → 401 Unauthorized
curl -i http://localhost:8080/api/restaurants/1/menus
# 응답: HTTP/1.1 401 Unauthorized


# 4. JWT 토큰으로 보호된 API 접근 → 200 OK
curl -i http://localhost:8080/api/restaurants/1/menus \
  -H "Authorization: Bearer $TOKEN"
# 응답: HTTP/1.1 200 OK
# [{"id":1,"name":"김치찌개",...}]


# 5. 잘못된 토큰으로 접근 → 401 Unauthorized
curl -i http://localhost:8080/api/restaurants/1/menus \
  -H "Authorization: Bearer invalid.token.here"
# 응답: HTTP/1.1 401 Unauthorized


# 6. 역할 기반 접근 제어 (ADMIN 전용 API)
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $TOKEN"
# CUSTOMER 역할로는 접근 불가 → 403 Forbidden
```

---

## 정리

```bash
# Redis 캐시 전체 삭제 (필요 시)
docker exec grit-redis redis-cli FLUSHALL

# 취약한 앱 및 MySQL 종료
docker compose --profile security down

# 모든 컨테이너 종료
docker compose down
```

## 핵심 정리

| 항목 | 내용 |
|------|------|
| **SQL Injection** | 사용자 입력을 SQL 쿼리에 직접 삽입하여 DB를 조작하는 공격 |
| **Prepared Statement** | 쿼리와 데이터를 분리하여 SQL Injection 방어 (`SELECT * WHERE name = ?`) |
| **XSS (Cross-Site Scripting)** | 악의적인 스크립트를 웹 페이지에 삽입하여 실행하는 공격 |
| **HTML 이스케이프** | `<`, `>`, `&` 등을 `&lt;`, `&gt;`, `&amp;`로 변환하여 XSS 방어 |
| **BCrypt** | 솔트(salt)를 자동 생성하는 단방향 해시 함수, 비밀번호 저장에 필수 |
| **JWT (JSON Web Token)** | 서명된 JSON 토큰으로 Stateless 인증 구현, Header.Payload.Signature 구조 |
| **CORS** | Cross-Origin Resource Sharing, 다른 도메인에서의 API 호출 허용 |
| **CSRF** | Cross-Site Request Forgery, 세션 쿠키를 악용한 위조 요청 공격 (JWT는 불필요) |
| **SecurityContext** | Spring Security에서 현재 인증 정보를 저장하는 ThreadLocal 컨텍스트 |
| **STATELESS 세션** | 서버가 세션을 저장하지 않고, 모든 요청에 JWT 포함 (수평 확장 용이) |

## 더 해보기 (선택)

- [ ] SQL Injection 고급 공격: UNION, Blind SQL Injection, Time-based Injection 시도
- [ ] OWASP ZAP 또는 Burp Suite로 자동화된 취약점 스캔 실행
- [ ] CSP (Content Security Policy) 헤더를 Spring Security에 추가하여 XSS 이중 방어
- [ ] JWT Refresh Token 구현: Access Token 만료 시 재발급 로직
- [ ] Rate Limiting: 로그인 API에 Brute Force 공격 방지 (Spring Bucket4j)
- [ ] 비밀번호 정책 강화: 최소 길이, 특수문자, 숫자 포함 검증
- [ ] Argon2 해싱: BCrypt보다 최신의 안전한 해싱 알고리즘 적용
- [ ] Spring Security Method-level 권한: `@PreAuthorize("hasRole('ADMIN')")` 사용
- [ ] JWT 블랙리스트: 로그아웃한 토큰을 Redis에 저장하여 무효화
- [ ] HTTPS 적용: Let's Encrypt 인증서로 TLS/SSL 암호화 통신 구성
