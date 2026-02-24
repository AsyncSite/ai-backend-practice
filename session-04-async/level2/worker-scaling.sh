#!/bin/bash

# [Session 04 - Level 2] Worker 수에 따른 처리 속도 비교
#
# RabbitMQ Worker를 1개, 2개, 4개로 늘려가며
# 메시지 처리 속도가 어떻게 변하는지 확인합니다.

echo "=================================="
echo "Worker Scaling 실험"
echo "=================================="

API_URL="http://localhost:8080/api/orders"
RABBITMQ_MGMT="http://localhost:15672"

# 테스트할 메시지 수
MESSAGE_COUNT=50

echo ""
echo "📋 실험 시나리오"
echo "----------------------------------"
echo "1. ${MESSAGE_COUNT}개의 주문을 한꺼번에 생성"
echo "2. Worker 수에 따른 처리 시간 측정"
echo "3. 처리율 비교"
echo ""

# ============================================
# 함수: 큐 초기화
# ============================================
clear_queue() {
  echo "큐 초기화 중..."
  docker exec grit-rabbitmq rabbitmqadmin purge queue name=order.notification.queue > /dev/null 2>&1
  echo "✓ 큐 비우기 완료"
}

# ============================================
# 함수: 메시지 발행
# ============================================
publish_messages() {
  local count=$1
  echo "${count}개의 메시지 발행 중..."

  for i in $(seq 1 $count); do
    curl -s -o /dev/null \
      -X POST ${API_URL} \
      -H "Content-Type: application/json" \
      -d "{\"userId\": 1, \"restaurantId\": 1, \"items\": [{\"menuId\": 1, \"quantity\": 2}]}" &
  done

  wait
  echo "✓ 메시지 발행 완료"
}

# ============================================
# 함수: 큐 메시지 수 확인
# ============================================
get_queue_size() {
  curl -s -u guest:guest \
    ${RABBITMQ_MGMT}/api/queues/%2F/order.notification.queue \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('messages', 0))" 2>/dev/null || echo "0"
}

# ============================================
# 함수: 처리 완료 대기 및 시간 측정
# ============================================
wait_for_processing() {
  local start_time=$(date +%s)
  local prev_count=$(get_queue_size)

  echo "처리 중..."
  while true; do
    sleep 1
    local current_count=$(get_queue_size)

    # 진행 상황 출력
    local processed=$((MESSAGE_COUNT - current_count))
    echo -ne "\r처리 진행: ${processed}/${MESSAGE_COUNT} (큐: ${current_count})"

    # 모든 메시지 처리 완료
    if [ "$current_count" -eq 0 ]; then
      echo ""
      break
    fi

    # 10초 동안 변화 없으면 타임아웃
    if [ "$current_count" -eq "$prev_count" ]; then
      local elapsed=$(($(date +%s) - start_time))
      if [ $elapsed -gt 10 ]; then
        echo ""
        echo "⚠️  타임아웃: Worker가 동작하지 않는 것 같습니다."
        return 1
      fi
    else
      start_time=$(date +%s)
    fi

    prev_count=$current_count
  done

  local end_time=$(date +%s)
  local total_time=$((end_time - start_time))
  echo $total_time
}

# ============================================
# 실험 1: Worker 1개 (기본)
# ============================================
echo ""
echo "실험 1: Worker 1개"
echo "----------------------------------"

clear_queue
publish_messages $MESSAGE_COUNT

echo "처리 시작 시간: $(date +%H:%M:%S)"
time_worker_1=$(wait_for_processing)

if [ $? -eq 0 ]; then
  echo "✓ 처리 완료: ${time_worker_1}초"
  throughput_1=$(echo "scale=2; $MESSAGE_COUNT / $time_worker_1" | bc)
  echo "처리율: ${throughput_1} msg/s"
else
  echo "✗ 실험 실패"
  exit 1
fi

# ============================================
# 실험 2: Worker 2개
# ============================================
echo ""
echo "실험 2: Worker 2개"
echo "----------------------------------"
echo "application.yml에서 다음 설정을 변경하세요:"
echo "  spring.rabbitmq.listener.simple.concurrency: 2"
echo ""

read -p "Worker를 2개로 변경하고 앱을 재시작했나요? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "먼저 설정을 변경하고 재시작하세요:"
    echo "  docker compose up -d --build app"
    exit 1
fi

clear_queue
publish_messages $MESSAGE_COUNT

echo "처리 시작 시간: $(date +%H:%M:%S)"
time_worker_2=$(wait_for_processing)

if [ $? -eq 0 ]; then
  echo "✓ 처리 완료: ${time_worker_2}초"
  throughput_2=$(echo "scale=2; $MESSAGE_COUNT / $time_worker_2" | bc)
  echo "처리율: ${throughput_2} msg/s"
else
  echo "✗ 실험 실패"
  exit 1
fi

# ============================================
# 실험 3: Worker 4개
# ============================================
echo ""
echo "실험 3: Worker 4개"
echo "----------------------------------"
echo "application.yml에서 다음 설정을 변경하세요:"
echo "  spring.rabbitmq.listener.simple.concurrency: 4"
echo ""

read -p "Worker를 4개로 변경하고 앱을 재시작했나요? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "먼저 설정을 변경하고 재시작하세요:"
    echo "  docker compose up -d --build app"
    exit 1
fi

clear_queue
publish_messages $MESSAGE_COUNT

echo "처리 시작 시간: $(date +%H:%M:%S)"
time_worker_4=$(wait_for_processing)

if [ $? -eq 0 ]; then
  echo "✓ 처리 완료: ${time_worker_4}초"
  throughput_4=$(echo "scale=2; $MESSAGE_COUNT / $time_worker_4" | bc)
  echo "처리율: ${throughput_4} msg/s"
else
  echo "✗ 실험 실패"
  exit 1
fi

# ============================================
# 결과 비교
# ============================================
echo ""
echo "=================================="
echo "실험 결과 비교"
echo "=================================="
echo ""
printf "%-15s %10s %15s %15s\n" "Worker 수" "처리 시간" "처리율" "개선율"
echo "----------------------------------------------------------"
printf "%-15s %10ss %15s msg/s %15s\n" "1개" "$time_worker_1" "$throughput_1" "기준"

improvement_2=$(echo "scale=1; (1 - $time_worker_2 / $time_worker_1) * 100" | bc)
printf "%-15s %10ss %15s msg/s %15s%%\n" "2개" "$time_worker_2" "$throughput_2" "$improvement_2"

improvement_4=$(echo "scale=1; (1 - $time_worker_4 / $time_worker_1) * 100" | bc)
printf "%-15s %10ss %15s msg/s %15s%%\n" "4개" "$time_worker_4" "$throughput_4" "$improvement_4"

echo ""
echo "💡 관찰 포인트:"
echo "  - Worker를 2배로 늘렸을 때 처리 시간이 절반으로 줄어들었나요?"
echo "  - Worker를 4배로 늘렸을 때는? (선형적으로 개선되지 않을 수 있음)"
echo "  - CPU 코어 수와 Worker 수의 관계를 고려해보세요"
echo ""
echo "🎯 최적 Worker 수 결정:"
echo "  - CPU 코어 수를 초과하면 컨텍스트 스위칭 오버헤드 발생"
echo "  - I/O 대기가 많은 작업이면 코어 수보다 많은 Worker 가능"
echo "  - 메모리 사용량도 함께 고려 (Worker당 메모리 사용)"
echo ""
