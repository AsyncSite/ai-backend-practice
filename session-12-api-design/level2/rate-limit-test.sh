#!/bin/bash

# [Session 12 - Level 2] Rate Limiting 테스트

echo "=================================="
echo "Rate Limiting 테스트"
echo "=================================="

API_URL="http://localhost:8080/api/restaurants"

echo ""
echo "Rate Limit 정책 (예상):"
echo "  - 1분당 최대 60회 요청"
echo "  - 초과 시 429 Too Many Requests"
echo ""

echo "빠르게 70번 요청을 보냅니다..."
echo ""

success_count=0
rate_limited_count=0

for i in {1..70}; do
  http_code=$(curl -s -o /dev/null -w "%{http_code}" ${API_URL})
  
  if [ "$http_code" = "200" ]; then
    echo -ne "\r✓ 요청 $i: 성공 (200)"
    ((success_count++))
  elif [ "$http_code" = "429" ]; then
    echo -ne "\r⚠️  요청 $i: Rate Limit 초과 (429)"
    ((rate_limited_count++))
  else
    echo -ne "\r✗ 요청 $i: 에러 ($http_code)"
  fi
  
  sleep 0.1
done

echo ""
echo ""
echo "=================================="
echo "결과"
echo "=================================="
echo "성공: ${success_count}회"
echo "Rate Limited: ${rate_limited_count}회"

if [ $rate_limited_count -gt 0 ]; then
  echo ""
  echo "✓ Rate Limiting이 동작합니다!"
else
  echo ""
  echo "⚠️  Rate Limiting이 설정되지 않았거나 제한이 높습니다."
fi

echo ""
echo "💡 Rate Limiting 구현 방법:"
echo "  - Bucket4j (Token Bucket 알고리즘)"
echo "  - Redis (분산 환경)"
echo "  - API Gateway (Nginx, Kong)"
