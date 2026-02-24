#!/bin/bash

# [Session 12 - Level 1] REST API 테스트 스크립트

echo "=================================="
echo "REST API 테스트"
echo "=================================="

BASE_URL="http://localhost:8080/api"

echo ""
echo "1. Swagger UI 확인"
echo "----------------------------------"
echo "브라우저에서 확인: http://localhost:8080/swagger-ui.html"

echo ""
echo "2. GET - 목록 조회"
echo "----------------------------------"
curl -s "${BASE_URL}/restaurants" | python3 -m json.tool | head -30

echo ""
echo "3. GET - 단건 조회"
echo "----------------------------------"
curl -s "${BASE_URL}/restaurants/1" | python3 -m json.tool

echo ""
echo "4. POST - 생성"
echo "----------------------------------"
curl -s -X POST "${BASE_URL}/orders" \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "restaurantId": 1, "items": [{"menuId": 1, "quantity": 2}]}' \
  | python3 -m json.tool

echo ""
echo "5. 페이지네이션"
echo "----------------------------------"
echo "페이지 0, 크기 3:"
curl -s "${BASE_URL}/restaurants?page=0&size=3" | python3 -m json.tool

echo ""
echo "페이지 1, 크기 3:"
curl -s "${BASE_URL}/restaurants?page=1&size=3" | python3 -m json.tool

echo ""
echo "6. 정렬"
echo "----------------------------------"
curl -s "${BASE_URL}/restaurants?sort=name,asc" | python3 -m json.tool | head -20

echo ""
echo "7. 에러 응답 (404)"
echo "----------------------------------"
curl -s "${BASE_URL}/restaurants/99999" | python3 -m json.tool

echo ""
echo "=================================="
echo "💡 REST API 설계 원칙"
echo "=================================="
echo "- 리소스 중심 URL (/restaurants, /orders)"
echo "- HTTP 메서드 활용 (GET, POST, PUT, DELETE)"
echo "- 일관된 응답 형식"
echo "- 적절한 HTTP 상태 코드"
