#!/bin/bash
# =============================================================================
# [Session 06 - Level 1] 로드밸런싱 + 장애 우회 테스트 스크립트
# =============================================================================
# 사용법: bash ha-test.sh
# 사전 조건: docker compose --profile ha up -d
# =============================================================================

set -e

echo "=== Session 06: 고가용성 실습 ==="
echo ""

# Step 1: 로드밸런싱 확인
echo "=== Step 1: Round Robin 분배 확인 ==="
echo "(10번 요청하여 app-1, app-2 번갈아 응답하는지 확인)"
echo ""

for i in {1..10}; do
  RESPONSE=$(curl -s http://localhost/api/server-info 2>/dev/null)
  SERVER_ID=$(echo "${RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])" 2>/dev/null || echo "ERROR")
  echo "  요청 ${i}: ${SERVER_ID}"
done
echo ""

# Step 2: 서버 1대 중지
echo "=== Step 2: app-replica 중지 ==="
docker stop grit-app-replica 2>/dev/null || true
sleep 3
echo "  grit-app-replica 중지됨"
echo ""

echo "  모든 요청이 app-1로만 가는지 확인:"
for i in {1..5}; do
  RESPONSE=$(curl -s http://localhost/api/server-info 2>/dev/null)
  SERVER_ID=$(echo "${RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])" 2>/dev/null || echo "ERROR")
  echo "  요청 ${i}: ${SERVER_ID}"
done
echo ""

# Step 3: 서버 복구
echo "=== Step 3: app-replica 재시작 ==="
docker start grit-app-replica 2>/dev/null || true
echo "  서버 시작 대기 (15초)..."
sleep 15
echo ""

echo "  다시 양쪽으로 분배되는지 확인:"
for i in {1..10}; do
  RESPONSE=$(curl -s http://localhost/api/server-info 2>/dev/null)
  SERVER_ID=$(echo "${RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin)['serverId'])" 2>/dev/null || echo "ERROR")
  echo "  요청 ${i}: ${SERVER_ID}"
done
echo ""

echo "=== 실습 완료 ==="
