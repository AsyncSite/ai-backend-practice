#!/bin/bash
# =============================================================================
# [Session 05 - Level 1] 경쟁 상태(Race Condition) 재현 스크립트
# =============================================================================
# 이 스크립트는 동시 요청으로 재고 정합성 문제를 재현합니다.
#
# 사용법: bash concurrency-test.sh
# =============================================================================

set -e

APP_URL="http://localhost:8080"
MENU_ID=1
INITIAL_STOCK=100
CONCURRENT_REQUESTS=50

echo "=== Session 05: 동시성 실습 - 경쟁 상태 재현 ==="
echo ""

# Step 1: 재고 초기화
echo "=== Step 1: 재고를 ${INITIAL_STOCK}으로 초기화 ==="
docker exec -it grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = ${INITIAL_STOCK} WHERE id = ${MENU_ID};" 2>/dev/null
echo "초기 재고: ${INITIAL_STOCK}"
echo ""

# Step 2: 잠금 없이 동시 요청
echo "=== Step 2: 잠금 없이 ${CONCURRENT_REQUESTS}개 동시 요청 ==="
echo "(각 요청이 재고 1개씩 차감)"
echo ""

for i in $(seq 1 ${CONCURRENT_REQUESTS}); do
  curl -s -X POST "${APP_URL}/api/menus/${MENU_ID}/decrease-stock?quantity=1" > /dev/null &
done
wait

# 결과 확인
REMAINING=$(docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -sN -e "SELECT stock FROM menus WHERE id = ${MENU_ID};" 2>/dev/null)

EXPECTED=$((INITIAL_STOCK - CONCURRENT_REQUESTS))
echo "기대 재고: ${EXPECTED}"
echo "실제 재고: ${REMAINING}"

if [ "${REMAINING}" -eq "${EXPECTED}" ]; then
  echo "결과: 정합성 유지됨 (운이 좋았거나 동시성이 충분하지 않음)"
else
  LOST=$((REMAINING - EXPECTED))
  echo "결과: 정합성 깨짐! ${LOST}개의 차감이 누락되었습니다."
  echo "       이것이 경쟁 상태(Race Condition)입니다!"
fi
echo ""

# Step 3: 재고 재초기화
echo "=== Step 3: 재고를 다시 ${INITIAL_STOCK}으로 초기화 ==="
docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -e "UPDATE menus SET stock = ${INITIAL_STOCK} WHERE id = ${MENU_ID};" 2>/dev/null
echo ""

# Step 4: 비관적 잠금으로 동시 요청
echo "=== Step 4: 비관적 잠금으로 ${CONCURRENT_REQUESTS}개 동시 요청 ==="

for i in $(seq 1 ${CONCURRENT_REQUESTS}); do
  curl -s -X POST "${APP_URL}/api/menus/${MENU_ID}/decrease-stock-pessimistic?quantity=1" > /dev/null &
done
wait

REMAINING_LOCKED=$(docker exec grit-mysql mysql -uroot -proot1234 backend_study \
  -sN -e "SELECT stock FROM menus WHERE id = ${MENU_ID};" 2>/dev/null)

echo "기대 재고: ${EXPECTED}"
echo "실제 재고: ${REMAINING_LOCKED}"

if [ "${REMAINING_LOCKED}" -eq "${EXPECTED}" ]; then
  echo "결과: 정합성 유지됨! 비관적 잠금이 동시 접근을 순차 처리했습니다."
else
  echo "결과: 예상과 다릅니다. 로그를 확인하세요."
fi

echo ""
echo "=== 실습 완료 ==="
echo "잠금 없음: 재고 ${REMAINING} (기대: ${EXPECTED})"
echo "비관적 잠금: 재고 ${REMAINING_LOCKED} (기대: ${EXPECTED})"
