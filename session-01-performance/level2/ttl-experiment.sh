#!/bin/bash

# [Session 01 - Level 2] TTL 변경 실험 스크립트
#
# TTL(Time To Live)을 변경하며 캐시 적중률 변화를 관찰합니다.
# 짧은 TTL vs 긴 TTL의 트레이드오프를 이해할 수 있습니다.

echo "=================================="
echo "TTL 실험: 캐시 만료 시간에 따른 동작"
echo "=================================="

RESTAURANT_ID=1
API_URL="http://localhost:8080/api/restaurants/${RESTAURANT_ID}/menus"

echo ""
echo "📋 실험 시나리오"
echo "----------------------------------"
echo "1. TTL을 1초로 설정 (application.yml 수정 필요)"
echo "   spring.cache.redis.time-to-live: 1000"
echo ""
echo "2. 빠르게 3번 요청 -> 1초 이내이므로 캐시 히트 예상"
echo ""
echo "3. 2초 대기 후 다시 요청 -> 캐시 만료되어 미스 예상"
echo ""

read -p "TTL을 1초로 변경하셨나요? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "먼저 application.yml에서 TTL을 변경하고 앱을 재시작하세요:"
    echo "  docker compose up -d --build app"
    exit 1
fi

echo ""
echo "1. 캐시 초기화"
echo "----------------------------------"
docker exec grit-redis redis-cli FLUSHALL > /dev/null 2>&1
echo "모든 캐시 삭제 완료"

echo ""
echo "2. 빠르게 3번 연속 요청 (1초 이내)"
echo "----------------------------------"
for i in {1..3}; do
  echo -n "요청 $i: "
  curl -s -o /dev/null -w "%{time_total}s" ${API_URL}

  # TTL 확인
  TTL=$(docker exec grit-redis redis-cli TTL "grit::menus::${RESTAURANT_ID}" 2>/dev/null)
  echo " (TTL: ${TTL}초)"

  sleep 0.1  # 0.1초 간격 (총 0.3초)
done

echo ""
echo "3. 2초 대기 중... (캐시 만료 대기)"
echo "----------------------------------"
for i in {2..1}; do
  echo "${i}초 남음..."
  sleep 1
done

echo ""
echo "4. 2초 후 다시 요청 (캐시 만료 예상)"
echo "----------------------------------"
echo -n "요청: "
curl -s -o /dev/null -w "%{time_total}s" ${API_URL}

# TTL 확인
TTL=$(docker exec grit-redis redis-cli TTL "grit::menus::${RESTAURANT_ID}" 2>/dev/null)
echo " (TTL: ${TTL}초)"

echo ""
echo "5. Redis 통계 확인"
echo "----------------------------------"
echo "캐시 히트/미스 통계:"
docker exec grit-redis redis-cli INFO stats 2>/dev/null | grep -E "keyspace_hits|keyspace_misses"

echo ""
echo "=================================="
echo "TTL 실험 결과 분석"
echo "=================================="
echo ""
echo "💡 관찰 포인트:"
echo "  - TTL이 짧으면 데이터는 항상 신선하지만, 캐시 적중률이 낮아짐"
echo "  - TTL이 길면 캐시 적중률은 높지만, 오래된 데이터를 보여줄 위험"
echo ""
echo "🔄 다음 실험:"
echo "  1. TTL을 30초로 변경 (30000ms)"
echo "  2. TTL을 5분으로 변경 (300000ms - 기본값)"
echo "  3. 각각의 경우 캐시 적중률과 데이터 신선도 비교"
echo ""
