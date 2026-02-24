#!/bin/bash

# [Session 03 - Level 1] 타임아웃 동작 테스트 스크립트
#
# 외부 API 호출 시 타임아웃 설정의 중요성을 체감합니다.
# 모의 PG 서버의 랜덤 지연을 통해 타임아웃이 없을 때의 위험을 확인합니다.

echo "=================================="
echo "외부 API 타임아웃 테스트"
echo "=================================="

PG_API="http://localhost:9000/api/payments"

echo ""
echo "1. 모의 PG 서버 상태 확인"
echo "----------------------------------"

# 헬스체크
echo -n "PG 서버 상태: "
curl -s http://localhost:9000/health | grep -q "ok" && echo "✓ 정상" || echo "✗ 오류"

echo ""
echo "2. PG 서버 동작 방식"
echo "----------------------------------"
echo "이 모의 PG 서버는 랜덤하게 다음 중 하나를 수행합니다:"
echo "  - 즉시 성공 응답 (50%)"
echo "  - 1~3초 지연 후 응답 (30%)"
echo "  - 에러 응답 (20%)"
echo ""

echo ""
echo "3. 타임아웃 없이 10번 요청 (응답 시간 측정)"
echo "----------------------------------"

total_time=0
slow_count=0

for i in {1..10}; do
  # 각 요청의 응답 시간 측정
  response_time=$(curl -s -o /dev/null -w "%{time_total}" \
    -X POST ${PG_API} \
    -H "Content-Type: application/json" \
    -d "{\"amount\": 25000, \"orderId\": $i}")

  echo "요청 $i: ${response_time}s"

  # 2초 이상이면 slow로 카운트
  if (( $(echo "$response_time > 2.0" | bc -l) )); then
    ((slow_count++))
  fi

  # 총 시간 누적
  total_time=$(echo "$total_time + $response_time" | bc)
done

echo ""
echo "----------------------------------"
echo "전체 요청 시간: ${total_time}s"
echo "느린 요청 (>2s): ${slow_count}/10"
echo ""

if [ $slow_count -gt 0 ]; then
  echo "⚠️  타임아웃 없이 외부 API를 호출하면 느린 응답으로 인해"
  echo "    전체 시스템이 대기하게 되어 성능 저하가 발생합니다!"
fi

echo ""
echo "4. 타임아웃 적용 (curl의 --max-time 옵션)"
echo "----------------------------------"
echo "타임아웃 1초로 설정하여 동일 요청 실행:"
echo ""

timeout_count=0
success_count=0

for i in {1..10}; do
  # --max-time 1: 1초 타임아웃
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    --max-time 1 \
    -X POST ${PG_API} \
    -H "Content-Type: application/json" \
    -d "{\"amount\": 25000, \"orderId\": $i}")

  if [ "$http_code" = "000" ]; then
    echo "요청 $i: 타임아웃 (1초 초과)"
    ((timeout_count++))
  elif [ "$http_code" = "200" ]; then
    echo "요청 $i: 성공 (${http_code})"
    ((success_count++))
  else
    echo "요청 $i: 실패 (${http_code})"
  fi
done

echo ""
echo "----------------------------------"
echo "성공: ${success_count}/10"
echo "타임아웃: ${timeout_count}/10"
echo ""
echo "💡 타임아웃을 설정하면 느린 요청을 빠르게 포기하고"
echo "   다른 작업을 처리할 수 있습니다."

echo ""
echo "5. Spring Boot에서 타임아웃 설정 예시"
echo "----------------------------------"
cat << 'EOF'
RestTemplate 타임아웃 설정:

@Bean
public RestTemplate restTemplate() {
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(3000);  // 연결 타임아웃: 3초
    factory.setReadTimeout(5000);     // 읽기 타임아웃: 5초
    return new RestTemplate(factory);
}
EOF

echo ""
echo "=================================="
echo "테스트 완료!"
echo "=================================="
echo ""
echo "💡 핵심 포인트:"
echo "  - 타임아웃 없이 외부 API를 호출하면 무한 대기 위험"
echo "  - 타임아웃을 너무 짧게 설정하면 정상 요청도 실패"
echo "  - 타임아웃을 너무 길게 설정하면 느린 응답이 시스템을 블로킹"
echo "  - 적절한 타임아웃 값은 외부 API의 SLA를 고려하여 결정"
echo ""
