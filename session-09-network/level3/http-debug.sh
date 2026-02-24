#!/bin/bash
set -e

# =============================================================================
# [Session 09 - Level 3] HTTP 디버깅 및 네트워크 진단 실습
# =============================================================================
# 네트워크 문제를 진단하는 핵심 도구들을 실습합니다.
#
# 학습 목표:
#   1. curl로 HTTP 요청/응답을 상세히 분석할 수 있다
#   2. HTTP 헤더를 이해하고 분석할 수 있다
#   3. DNS 동작 원리를 확인할 수 있다
#   4. TCP 연결 과정을 추적할 수 있다
#
# 사전 조건:
#   docker compose up -d (앱 서버가 실행 중이어야 합니다)
# =============================================================================

BASE_URL="http://localhost:8080"

echo "====================================="
echo " HTTP 디버깅 실습 시작"
echo "====================================="

# =============================================================================
# 섹션 1: curl 기본 - HTTP 요청/응답 분석
# =============================================================================

echo ""
echo "--- 1. curl 기본 ---"

# TODO 1: API 응답을 보기 좋게 조회하세요
#
# curl 옵션:
#   -s: silent 모드 (진행 표시줄 숨김)
#   -X GET: HTTP 메서드 지정 (GET은 기본값이라 생략 가능)
#   | jq .: JSON을 보기 좋게 포맷팅 (jq 설치 필요)
#
# 힌트: curl -s ${BASE_URL}/api/restaurants | jq .
# 실행:
# curl -s ${BASE_URL}/api/restaurants | jq .

# TODO 2: HTTP 헤더만 조회하세요
#
# -I 옵션: HEAD 요청 (응답 본문 없이 헤더만 반환)
# -i 옵션: 응답 헤더 + 본문 함께 출력
#
# 확인할 헤더:
#   - Content-Type: 응답 형식 (application/json)
#   - Transfer-Encoding: chunked (스트리밍 전송) vs Content-Length
#   - Connection: keep-alive (TCP 연결 재사용)
#
# 힌트: curl -I ${BASE_URL}/api/restaurants
# 실행:
# curl -I ${BASE_URL}/api/restaurants


# =============================================================================
# 섹션 2: curl 고급 - 상세 디버깅
# =============================================================================

echo ""
echo "--- 2. curl 상세 디버깅 ---"

# TODO 3: verbose 모드로 전체 통신 과정을 확인하세요
#
# -v 옵션: TCP 연결, TLS 핸드셰이크, 요청/응답 헤더를 모두 보여줍니다
#   - > : 클라이언트 -> 서버 (요청)
#   - < : 서버 -> 클라이언트 (응답)
#   - * : curl 내부 정보 (DNS, TCP 연결 등)
#
# 힌트: curl -v ${BASE_URL}/api/restaurants/1
# 실행:
# curl -v ${BASE_URL}/api/restaurants/1

# TODO 4: HTTP 요청/응답의 각 단계별 소요 시간을 측정하세요
#
# curl의 -w 옵션으로 타이밍 정보를 출력할 수 있습니다.
# 어느 단계에서 시간이 오래 걸리는지 병목을 찾는 데 유용합니다.
#
# 주요 타이밍:
#   - time_namelookup: DNS 조회 시간
#   - time_connect: TCP 연결 시간
#   - time_starttransfer: 첫 바이트 수신까지 시간 (TTFB)
#   - time_total: 전체 요청 완료 시간
#
# 힌트:
#   curl -s -o /dev/null -w "\
#   DNS Lookup:    %{time_namelookup}s\n\
#   TCP Connect:   %{time_connect}s\n\
#   TLS Handshake: %{time_appconnect}s\n\
#   TTFB:          %{time_starttransfer}s\n\
#   Total:         %{time_total}s\n\
#   HTTP Code:     %{http_code}\n\
#   Size:          %{size_download} bytes\n" \
#   ${BASE_URL}/api/restaurants
# 실행:


# =============================================================================
# 섹션 3: HTTP 메서드 테스트
# =============================================================================

echo ""
echo "--- 3. HTTP 메서드 테스트 ---"

# TODO 5: POST 요청으로 데이터를 전송하세요
#
# curl 옵션:
#   -X POST: POST 메서드
#   -H "Content-Type: application/json": 요청 본문이 JSON임을 명시
#   -d '{"key":"value"}': 요청 본문 데이터
#
# 힌트:
#   curl -s -X POST ${BASE_URL}/api/orders \
#     -H "Content-Type: application/json" \
#     -d '{
#       "restaurantId": 1,
#       "items": [
#         {"menuId": 1, "quantity": 2}
#       ]
#     }' | jq .
# 실행:

# TODO 6: 다양한 HTTP 상태 코드를 확인하세요
#
# 존재하지 않는 리소스 요청 (404 확인):
# 힌트: curl -s -o /dev/null -w "HTTP %{http_code}\n" ${BASE_URL}/api/restaurants/99999
# 실행:

# 잘못된 요청 (400 확인):
# 힌트: curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST ${BASE_URL}/api/orders -H "Content-Type: application/json" -d '{}'
# 실행:


# =============================================================================
# 섹션 4: DNS 확인
# =============================================================================

echo ""
echo "--- 4. DNS 확인 ---"

# TODO 7: 도메인의 DNS 레코드를 조회하세요
#
# nslookup / dig 명령어: 도메인 -> IP 주소 변환 확인
#
# nslookup: 간단한 DNS 조회
# 힌트: nslookup google.com
# 실행:
# nslookup google.com

# TODO 8: dig로 상세 DNS 정보를 조회하세요
#
# dig 명령어: DNS 레코드 상세 정보
#   - A 레코드: 도메인 -> IPv4 주소
#   - AAAA 레코드: 도메인 -> IPv6 주소
#   - CNAME: 도메인 별칭
#   - Query time: DNS 조회 소요 시간
#
# 힌트: dig google.com +short
# 상세: dig google.com
# 실행:
# dig google.com +short


# =============================================================================
# 섹션 5: TCP 연결 테스트
# =============================================================================

echo ""
echo "--- 5. TCP 연결 테스트 ---"

# TODO 9: 특정 포트로 TCP 연결이 가능한지 확인하세요
#
# 서버 장애 시 첫 번째로 확인할 것: "포트가 열려 있는가?"
#
# nc (netcat) 명령어:
#   -z: 포트 스캔 모드 (데이터 전송 없이 연결만 테스트)
#   -v: 상세 출력
#   -w 3: 3초 타임아웃
#
# 힌트:
#   echo "--- Spring Boot (8080) ---"
#   nc -zv localhost 8080 2>&1 || echo "연결 실패"
#
#   echo "--- MySQL (3306) ---"
#   nc -zv localhost 3306 2>&1 || echo "연결 실패"
#
#   echo "--- Redis (6379) ---"
#   nc -zv localhost 6379 2>&1 || echo "연결 실패"
# 실행:

# TODO 10: traceroute로 네트워크 경로를 추적하세요
#
# traceroute: 패킷이 목적지까지 거치는 네트워크 경로를 보여줍니다
#   - 각 홉(hop)의 IP 주소와 응답 시간
#   - * 표시: 해당 라우터가 응답하지 않음
#   - 특정 홉에서 갑자기 시간이 증가하면 그 구간이 병목
#
# 힌트: traceroute -m 10 google.com
# 대안 (Linux): tracepath google.com
# 실행:
# traceroute -m 10 google.com 2>/dev/null || tracepath google.com 2>/dev/null || echo "traceroute 미설치"


# =============================================================================
# 섹션 6: 종합 네트워크 진단 (심화)
# =============================================================================

echo ""
echo "--- 6. 종합 진단 ---"

# TODO 11: 아래 함수를 완성하여 서비스 연결 상태를 한눈에 확인하세요
#
# check_connectivity() {
#     echo "=== 서비스 연결 상태 진단 ==="
#     echo ""
#
#     # 1) Spring Boot 앱
#     echo -n "[Spring Boot] "
#     curl -s -o /dev/null -w "%{http_code}" ${BASE_URL}/actuator/health 2>/dev/null && echo " OK" || echo " FAIL"
#
#     # 2) MySQL
#     echo -n "[MySQL 3306] "
#     nc -zw3 localhost 3306 2>/dev/null && echo "OK" || echo "FAIL"
#
#     # 3) Redis
#     echo -n "[Redis 6379] "
#     nc -zw3 localhost 6379 2>/dev/null && echo "OK" || echo "FAIL"
#
#     # 4) Nginx (HA 프로필)
#     echo -n "[Nginx  80  ] "
#     nc -zw3 localhost 80 2>/dev/null && echo "OK" || echo "FAIL"
#
#     # 5) API 응답 시간
#     echo ""
#     echo "[API 응답 시간]"
#     curl -s -o /dev/null -w "  /api/restaurants: %{time_total}s\n" ${BASE_URL}/api/restaurants
#     curl -s -o /dev/null -w "  /actuator/health: %{time_total}s\n" ${BASE_URL}/actuator/health
# }
#
# 실행: check_connectivity

echo ""
echo "====================================="
echo " HTTP 디버깅 실습 완료"
echo "====================================="
