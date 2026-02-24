#!/bin/bash

# [Session 08 - Level 1] 서버 모니터링 명령어 가이드

echo "=================================="
echo "서버 모니터링 실습"
echo "=================================="

CONTAINER_NAME="grit-app"

echo ""
echo "1. 컨테이너 리소스 사용량"
echo "----------------------------------"
docker stats ${CONTAINER_NAME} --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"

echo ""
echo "2. 프로세스별 CPU/메모리 (top)"
echo "----------------------------------"
docker exec ${CONTAINER_NAME} top -bn1 | head -20

echo ""
echo "3. 메모리 상태 (free)"
echo "----------------------------------"
docker exec ${CONTAINER_NAME} free -m

echo ""
echo "4. 디스크 사용량 (df)"
echo "----------------------------------"
docker exec ${CONTAINER_NAME} df -h

echo ""
echo "5. 네트워크 연결 (netstat/ss)"
echo "----------------------------------"
if docker exec ${CONTAINER_NAME} which netstat > /dev/null 2>&1; then
  docker exec ${CONTAINER_NAME} netstat -tlnp 2>/dev/null | grep -E "Proto|LISTEN"
else
  docker exec ${CONTAINER_NAME} ss -tlnp 2>/dev/null | head -10
fi

echo ""
echo "6. 최근 로그 (20줄)"
echo "----------------------------------"
docker logs ${CONTAINER_NAME} --tail 20

echo ""
echo "7. 에러 로그 필터링"
echo "----------------------------------"
error_count=$(docker logs ${CONTAINER_NAME} 2>&1 | grep -i error | wc -l)
echo "ERROR 로그 개수: ${error_count}"
docker logs ${CONTAINER_NAME} 2>&1 | grep -i error | tail -5

echo ""
echo "=================================="
echo "모니터링 완료!"
echo "=================================="
