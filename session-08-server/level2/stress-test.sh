#!/bin/bash

# [Session 08 - Level 2] 부하 테스트 및 모니터링

echo "=================================="
echo "서버 부하 테스트"
echo "=================================="

CONTAINER_NAME="grit-app"
API_URL="http://localhost:8080/api/restaurants"

echo ""
echo "1. 현재 리소스 사용량 (기준선)"
echo "----------------------------------"
baseline=$(docker stats ${CONTAINER_NAME} --no-stream --format "{{.CPUPerc}}\t{{.MemPerc}}")
echo "CPU: $(echo $baseline | awk '{print $1}')"
echo "MEM: $(echo $baseline | awk '{print $2}')"

echo ""
echo "2. CPU 부하 발생 (10초)"
echo "----------------------------------"
docker exec ${CONTAINER_NAME} sh -c "dd if=/dev/urandom bs=1M count=200 2>/dev/null | md5sum" &
cpu_pid=$!

for i in {1..5}; do
  sleep 2
  stats=$(docker stats ${CONTAINER_NAME} --no-stream --format "{{.CPUPerc}}\t{{.MemPerc}}")
  echo "  ${i}회: CPU=$(echo $stats | awk '{print $1}'), MEM=$(echo $stats | awk '{print $2}')"
done

wait $cpu_pid 2>/dev/null

echo ""
echo "3. 네트워크 부하 (동시 요청)"
echo "----------------------------------"
for round in {1..5}; do
  echo "Round ${round}/5:"
  for i in {1..50}; do
    curl -s -o /dev/null ${API_URL} &
  done
  wait
  stats=$(docker stats ${CONTAINER_NAME} --no-stream --format "{{.CPUPerc}}\t{{.MemPerc}}")
  echo "  CPU=$(echo $stats | awk '{print $1}'), MEM=$(echo $stats | awk '{print $2}')"
  sleep 1
done

echo ""
echo "=================================="
echo "테스트 완료!"
echo "=================================="
