#!/bin/bash

# [Session 13 - Level 1] Actuator 메트릭 확인

echo "=================================="
echo "Spring Boot Actuator 메트릭"
echo "=================================="

ACTUATOR_URL="http://localhost:8080/actuator"

echo ""
echo "1. Actuator 엔드포인트 목록"
echo "----------------------------------"
curl -s ${ACTUATOR_URL} | python3 -m json.tool

echo ""
echo "2. 헬스체크"
echo "----------------------------------"
curl -s ${ACTUATOR_URL}/health | python3 -m json.tool

echo ""
echo "3. 사용 가능한 메트릭 목록"
echo "----------------------------------"
curl -s ${ACTUATOR_URL}/metrics | python3 -m json.tool | head -50

echo ""
echo "4. JVM 메모리 사용량"
echo "----------------------------------"
curl -s ${ACTUATOR_URL}/metrics/jvm.memory.used | python3 -m json.tool

echo ""
echo "5. HTTP 요청 메트릭"
echo "----------------------------------"
curl -s ${ACTUATOR_URL}/metrics/http.server.requests | python3 -m json.tool

echo ""
echo "6. Prometheus 형식 메트릭"
echo "----------------------------------"
curl -s ${ACTUATOR_URL}/prometheus | head -50

echo ""
echo "=================================="
echo "💡 주요 메트릭"
echo "=================================="
echo "jvm.memory.used: JVM 메모리 사용량"
echo "jvm.gc.pause: GC 일시정지 시간"
echo "http.server.requests: HTTP 요청 통계"
echo "system.cpu.usage: CPU 사용률"
echo "jdbc.connections.active: DB 연결 수"
