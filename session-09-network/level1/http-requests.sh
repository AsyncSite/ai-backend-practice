#!/bin/bash

# [Session 09 - Level 1] HTTP 요청/응답 분석 실습

echo "=================================="
echo "HTTP 요청/응답 분석"
echo "=================================="

API_URL="http://localhost:8080/api/restaurants"

echo ""
echo "1. 기본 GET 요청"
echo "----------------------------------"
curl ${API_URL}

echo ""
echo ""
echo "2. 상세 출력 (-v 옵션)"
echo "----------------------------------"
echo "요청/응답 헤더를 모두 확인할 수 있습니다."
curl -v ${API_URL}/1

echo ""
echo ""
echo "3. 응답 시간 측정 (-w 옵션)"
echo "----------------------------------"
curl -o /dev/null -s -w "HTTP %{http_code}\n시간: %{time_total}s\n" ${API_URL}

echo ""
echo "4. POST 요청 (JSON 데이터)"
echo "----------------------------------"
curl -X POST ${API_URL}/../orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}'

echo ""
echo ""
echo "5. 다양한 HTTP 상태 코드"
echo "----------------------------------"
echo -n "정상 (200): "
curl -s -o /dev/null -w "%{http_code}\n" ${API_URL}/1

echo -n "존재하지 않음 (404): "
curl -s -o /dev/null -w "%{http_code}\n" ${API_URL}/99999

echo ""
echo "6. 응답 헤더만 확인 (-I 옵션)"
echo "----------------------------------"
curl -I ${API_URL}

echo ""
echo "=================================="
echo "💡 유용한 curl 옵션"
echo "=================================="
echo "-v: 상세 출력 (헤더 포함)"
echo "-I: 헤더만 확인"
echo "-o: 출력을 파일로 저장"
echo "-s: 진행 표시 숨김"
echo "-w: 응답 시간 등 커스텀 출력"
echo "-X: HTTP 메서드 지정"
echo "-H: 헤더 추가"
echo "-d: POST 데이터"
