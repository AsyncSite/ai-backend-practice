#!/bin/bash

# [Session 04 - Level 1] RabbitMQ 메시지 발행/소비 테스트 스크립트
#
# 동기/비동기 방식의 응답 시간 차이를 측정하고,
# RabbitMQ를 통한 메시지 큐 동작을 확인합니다.

echo "=================================="
echo "RabbitMQ 비동기 처리 테스트"
echo "=================================="

API_URL="http://localhost:8080/api/orders"
RABBITMQ_MGMT="http://localhost:15672"

echo ""
echo "1. 환경 상태 확인"
echo "----------------------------------"

# 앱 서버 상태
echo -n "앱 서버: "
curl -s http://localhost:8080/actuator/health | grep -q "UP" && echo "✓ 정상" || echo "✗ 오류"

# RabbitMQ 상태
echo -n "RabbitMQ: "
curl -s -u guest:guest ${RABBITMQ_MGMT}/api/overview > /dev/null 2>&1 && echo "✓ 정상" || echo "✗ 오류"

echo ""
echo "2. RabbitMQ Management UI"
echo "----------------------------------"
echo "브라우저에서 확인: http://localhost:15672"
echo "계정: guest / guest"
echo ""
echo "확인할 내용:"
echo "  - Exchanges 탭: order.exchange 존재 여부"
echo "  - Queues 탭: order.notification.queue 존재 여부"

echo ""
echo "3. 동기 방식 (비교용) - 알림 처리가 완료될 때까지 대기"
echo "----------------------------------"
echo "동기 방식은 주문 생성 + 알림 발송을 순차적으로 처리합니다."
echo ""

# 동기 방식 응답 시간 측정 (3회)
sync_total=0
for i in {1..3}; do
  response_time=$(curl -s -o /dev/null -w "%{time_total}" \
    -X POST ${API_URL}/sync \
    -H "Content-Type: application/json" \
    -d "{\"userId\": 1, \"restaurantId\": 1, \"items\": [{\"menuId\": 1, \"quantity\": 2}]}")

  echo "요청 $i: ${response_time}s"
  sync_total=$(echo "$sync_total + $response_time" | bc)
done

sync_avg=$(echo "scale=3; $sync_total / 3" | bc)
echo "평균 응답 시간: ${sync_avg}s"

echo ""
echo "4. 비동기 방식 - 메시지를 큐에 발행하고 즉시 응답"
echo "----------------------------------"
echo "비동기 방식은 주문 생성 후 알림은 백그라운드에서 처리합니다."
echo ""

# 비동기 방식 응답 시간 측정 (3회)
async_total=0
for i in {1..3}; do
  response_time=$(curl -s -o /dev/null -w "%{time_total}" \
    -X POST ${API_URL} \
    -H "Content-Type: application/json" \
    -d "{\"userId\": 1, \"restaurantId\": 1, \"items\": [{\"menuId\": 1, \"quantity\": 2}]}")

  echo "요청 $i: ${response_time}s"
  async_total=$(echo "$async_total + $response_time" | bc)
done

async_avg=$(echo "scale=3; $async_total / 3" | bc)
echo "평균 응답 시간: ${async_avg}s"

echo ""
echo "5. 성능 비교"
echo "----------------------------------"
echo "동기 방식 평균: ${sync_avg}s"
echo "비동기 방식 평균: ${async_avg}s"

improvement=$(echo "scale=1; ($sync_avg - $async_avg) / $sync_avg * 100" | bc)
echo "개선율: ${improvement}%"

if (( $(echo "$async_avg < $sync_avg" | bc -l) )); then
  echo "✓ 비동기 방식이 더 빠릅니다!"
else
  echo "⚠️  예상과 다른 결과입니다. 다시 시도해보세요."
fi

echo ""
echo "6. RabbitMQ 큐 상태 확인"
echo "----------------------------------"

# 큐의 메시지 수 확인
queue_info=$(curl -s -u guest:guest \
  ${RABBITMQ_MGMT}/api/queues/%2F/order.notification.queue)

message_count=$(echo $queue_info | python3 -c "import sys,json; print(json.load(sys.stdin).get('messages', 0))" 2>/dev/null || echo "0")

echo "order.notification.queue에 대기 중인 메시지: ${message_count}개"

if [ "$message_count" -gt 0 ]; then
  echo "⚠️  메시지가 아직 처리되지 않았습니다. Worker가 동작 중인지 확인하세요."
else
  echo "✓ 모든 메시지가 처리되었습니다."
fi

echo ""
echo "7. 앱 로그에서 메시지 소비 확인"
echo "----------------------------------"
echo "최근 알림 처리 로그:"
docker logs grit-app --tail 20 2>&1 | grep -i "알림\|notification" || echo "(알림 로그 없음)"

echo ""
echo "8. 메시지 발행/소비 흐름 확인"
echo "----------------------------------"
echo "10개의 주문을 빠르게 생성하여 큐 동작을 관찰합니다."
echo ""

for i in {1..10}; do
  curl -s -o /dev/null \
    -X POST ${API_URL} \
    -H "Content-Type: application/json" \
    -d "{\"userId\": 1, \"restaurantId\": 1, \"items\": [{\"menuId\": 1, \"quantity\": 2}]}" &
done

wait
echo "10개 주문 생성 완료"

# 잠시 대기 후 큐 상태 재확인
sleep 2

queue_info=$(curl -s -u guest:guest \
  ${RABBITMQ_MGMT}/api/queues/%2F/order.notification.queue)

message_count=$(echo $queue_info | python3 -c "import sys,json; print(json.load(sys.stdin).get('messages', 0))" 2>/dev/null || echo "0")
processed=$(echo $queue_info | python3 -c "import sys,json; print(json.load(sys.stdin).get('messages_ready', 0) + json.load(sys.stdin).get('messages_unacknowledged', 0))" 2>/dev/null || echo "0")

echo ""
echo "큐 상태:"
echo "  - 대기 중: ${message_count}개"
echo "  - 처리율: $(curl -s -u guest:guest ${RABBITMQ_MGMT}/api/queues/%2F/order.notification.queue | python3 -c "import sys,json; print(json.load(sys.stdin).get('message_stats', {}).get('deliver_get_details', {}).get('rate', 0))" 2>/dev/null || echo "0") msg/s"

echo ""
echo "=================================="
echo "테스트 완료!"
echo "=================================="
echo ""
echo "💡 핵심 포인트:"
echo "  - 비동기 처리는 응답 시간을 크게 개선 (사용자 경험 향상)"
echo "  - 메시지 큐는 Producer와 Consumer를 분리 (느슨한 결합)"
echo "  - Worker 수를 늘려 처리 속도 조절 가능 (스케일 아웃)"
echo ""
echo "🔍 다음 단계:"
echo "  - RabbitMQ Management UI에서 그래프 확인"
echo "  - Worker 수를 2개, 4개로 늘려보기 (Level 2)"
echo "  - Dead Letter Queue 설정 (Level 3)"
echo ""
