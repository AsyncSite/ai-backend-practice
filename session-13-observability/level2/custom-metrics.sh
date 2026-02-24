#!/bin/bash

# [Session 13 - Level 2] 커스텀 메트릭 트래픽 생성

echo "=================================="
echo "커스텀 메트릭 트래픽 생성"
echo "=================================="

BASE_URL="http://localhost:8080/api"

echo ""
echo "트래픽을 생성하여 메트릭 데이터를 쌓습니다..."
echo ""

echo "1. 주문 생성 (50회)"
echo "----------------------------------"
for i in {1..50}; do
  curl -s -X POST ${BASE_URL}/orders \
    -H "Content-Type: application/json" \
    -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}' \
    > /dev/null &
  
  if [ $((i % 10)) -eq 0 ]; then
    echo "  $i/50 완료"
    wait
  fi
done
wait

echo ""
echo "2. 가게 조회 (100회)"
echo "----------------------------------"
for i in {1..100}; do
  curl -s ${BASE_URL}/restaurants > /dev/null &
  
  if [ $((i % 20)) -eq 0 ]; then
    echo "  $i/100 완료"
    wait
  fi
done
wait

echo ""
echo "3. 메뉴 조회 (50회)"
echo "----------------------------------"
for i in {1..50}; do
  restaurant_id=$((i % 5 + 1))
  curl -s ${BASE_URL}/restaurants/${restaurant_id}/menus > /dev/null &
  
  if [ $((i % 10)) -eq 0 ]; then
    echo "  $i/50 완료"
    wait
  fi
done
wait

echo ""
echo "=================================="
echo "트래픽 생성 완료!"
echo "=================================="
echo ""
echo "이제 다음을 확인하세요:"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin)"
echo "  - Actuator: http://localhost:8080/actuator/metrics"
