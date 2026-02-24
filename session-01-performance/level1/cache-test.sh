#!/bin/bash

# [Session 01 - Level 1] 캐시 히트/미스 테스트 스크립트
#
# 이 스크립트는 캐시 적용 전/후의 응답 시간 차이를 측정합니다.
# Redis 캐시가 동작하는지 확인하고 성능 개선을 직접 체감할 수 있습니다.

echo "=================================="
echo "Redis 캐시 성능 테스트"
echo "=================================="

# 테스트할 가게 ID
RESTAURANT_ID=1
API_URL="http://localhost:8080/api/restaurants/${RESTAURANT_ID}/menus"

echo ""
echo "1. 환경 상태 확인"
echo "----------------------------------"

# 앱 서버 상태 확인
echo -n "앱 서버 상태: "
curl -s http://localhost:8080/actuator/health | grep -q "UP" && echo "✓ 정상" || echo "✗ 오류"

# Redis 연결 확인
echo -n "Redis 연결: "
docker exec grit-redis redis-cli ping 2>/dev/null | grep -q "PONG" && echo "✓ 정상" || echo "✗ 오류"

echo ""
echo "2. 기존 캐시 삭제 (깨끗한 상태에서 시작)"
echo "----------------------------------"
docker exec grit-redis redis-cli DEL "grit::menus::${RESTAURANT_ID}" > /dev/null 2>&1
echo "캐시 키 삭제 완료"

echo ""
echo "3. 첫 번째 요청 (캐시 미스 - DB 조회)"
echo "----------------------------------"
curl -s -o /dev/null -w "응답 시간: %{time_total}s\n" ${API_URL}

echo ""
echo "4. 연속 5회 요청 (캐시 히트 - Redis 조회)"
echo "----------------------------------"
for i in {1..5}; do
  curl -s -o /dev/null -w "요청 $i: %{time_total}s\n" ${API_URL}
  sleep 0.2  # 0.2초 간격
done

echo ""
echo "5. Redis에서 캐시 데이터 확인"
echo "----------------------------------"

# 캐시 키 존재 여부
echo -n "캐시 키 존재: "
CACHE_EXISTS=$(docker exec grit-redis redis-cli EXISTS "grit::menus::${RESTAURANT_ID}" 2>/dev/null)
if [ "$CACHE_EXISTS" = "1" ]; then
  echo "✓ 있음"

  # TTL 확인
  TTL=$(docker exec grit-redis redis-cli TTL "grit::menus::${RESTAURANT_ID}" 2>/dev/null)
  echo "TTL (남은 시간): ${TTL}초"

  # 캐시 데이터 크기 확인
  echo -n "캐시 데이터: "
  docker exec grit-redis redis-cli GET "grit::menus::${RESTAURANT_ID}" 2>/dev/null | wc -c | xargs echo "bytes"
else
  echo "✗ 없음"
fi

echo ""
echo "6. 캐시 삭제 후 재확인 (다시 DB 조회)"
echo "----------------------------------"
docker exec grit-redis redis-cli DEL "grit::menus::${RESTAURANT_ID}" > /dev/null 2>&1
echo "캐시 삭제 완료"
curl -s -o /dev/null -w "응답 시간: %{time_total}s (캐시 미스)\n" ${API_URL}

echo ""
echo "7. 앱 로그에서 캐시 동작 확인"
echo "----------------------------------"
echo "최근 캐시 관련 로그:"
docker logs grit-app --tail 10 2>&1 | grep -i "캐시\|cache" || echo "(캐시 로그 없음)"

echo ""
echo "=================================="
echo "테스트 완료!"
echo "=================================="
echo ""
echo "💡 관찰 포인트:"
echo "  - 첫 요청(캐시 미스)과 이후 요청(캐시 히트)의 응답 시간 차이"
echo "  - Redis에 저장된 캐시 데이터의 TTL"
echo "  - 앱 로그에서 DB 조회 여부"
echo ""
