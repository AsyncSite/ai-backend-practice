#!/bin/bash

# [Session 09 - Level 3] 네트워크 디버깅 시나리오

echo "=================================="
echo "네트워크 디버깅 실습"
echo "=================================="

TARGET="localhost:8080"

echo ""
echo "1. 포트 연결 확인 (telnet/nc)"
echo "----------------------------------"
if command -v nc > /dev/null; then
  echo "nc로 포트 확인:"
  nc -zv localhost 8080 2>&1
elif command -v telnet > /dev/null; then
  echo "telnet으로 포트 확인:"
  timeout 2 telnet localhost 8080 2>&1 || echo "연결 실패"
fi

echo ""
echo "2. HTTP 요청 수동 작성"
echo "----------------------------------"
echo "GET /api/restaurants HTTP/1.1
Host: localhost:8080
Connection: close

" | nc localhost 8080

echo ""
echo "3. 네트워크 경로 추적"
echo "----------------------------------"
traceroute -m 5 google.com 2>/dev/null || echo "traceroute 없음"

echo ""
echo "=================================="
echo "💡 디버깅 체크리스트"
echo "=================================="
echo "1. 포트가 열려있는가? (nc/telnet)"
echo "2. DNS 해석이 되는가? (nslookup/dig)"
echo "3. 네트워크 연결이 가능한가? (ping)"
echo "4. 방화벽이 막고 있는가?"
echo "5. 타임아웃 설정은 적절한가?"
