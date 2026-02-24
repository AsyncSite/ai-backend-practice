#!/bin/bash

# [Session 06 - Level 2] 헬스체크 모니터링 스크립트

echo "=================================="
echo "헬스체크 모니터링"
echo "=================================="

HEALTH_URL="http://localhost/api/server-info"
CHECK_INTERVAL=2
MAX_FAILURES=3

consecutive_failures=0
is_healthy=true

echo ""
echo "📋 모니터링 설정"
echo "헬스체크 URL: ${HEALTH_URL}"
echo "체크 간격: ${CHECK_INTERVAL}초"
echo "장애 임계값: ${MAX_FAILURES}번 연속 실패"
echo ""

check_count=0
failure_count=0

while true; do
  check_count=$((check_count + 1))
  current_time=$(date +%H:%M:%S)

  http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 ${HEALTH_URL} 2>/dev/null)

  if [ "$http_code" = "200" ]; then
    if [ "$is_healthy" = false ]; then
      echo ""
      echo "✅ [${current_time}] 복구됨!"
      is_healthy=true
    fi

    server_id=$(curl -s ${HEALTH_URL} 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('serverId', 'unknown'))" 2>/dev/null || echo "unknown")
    echo -ne "\r✓ [${current_time}] 체크 #${check_count}: 정상 (${server_id})"

    consecutive_failures=0
  else
    consecutive_failures=$((consecutive_failures + 1))
    failure_count=$((failure_count + 1))

    echo ""
    echo "⚠️  [${current_time}] 헬스체크 실패 (${consecutive_failures}/${MAX_FAILURES})"

    if [ $consecutive_failures -ge $MAX_FAILURES ] && [ "$is_healthy" = true ]; then
      echo ""
      echo "🚨 장애 감지!"
      echo "=================================="
      echo "연속 ${MAX_FAILURES}번 헬스체크 실패"
      echo "시간: $(date)"
      echo "=================================="
      is_healthy=false
    fi
  fi

  sleep $CHECK_INTERVAL
done
