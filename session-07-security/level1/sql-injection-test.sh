#!/bin/bash

# [Session 07 - Level 1] SQL Injection 자동화 테스트 스크립트
#
# 이 스크립트는 다양한 SQL Injection 공격 패턴을 자동으로 테스트합니다.
# 취약한 앱(http://localhost:9999)이 실행 중이어야 합니다.

set -e  # 에러 발생 시 스크립트 중단

# 색상 출력 설정
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

VULNERABLE_URL="http://localhost:9999"

echo "=================================================="
echo "  SQL Injection 자동화 테스트"
echo "=================================================="
echo ""

# 서버 상태 확인
echo -e "${BLUE}[1/6] 서버 상태 확인${NC}"
if curl -s -o /dev/null -w "%{http_code}" "$VULNERABLE_URL" | grep -q "200"; then
    echo -e "${GREEN}✓ 취약한 앱 서버 실행 중 (http://localhost:9999)${NC}"
else
    echo -e "${RED}✗ 서버가 응답하지 않습니다. 'docker compose --profile security up -d' 실행하세요.${NC}"
    exit 1
fi
echo ""

# 정상 검색 테스트
echo -e "${BLUE}[2/6] 정상 검색 테스트${NC}"
echo "검색어: Alice"
RESPONSE=$(curl -s "$VULNERABLE_URL/search-vulnerable?q=Alice")
if echo "$RESPONSE" | grep -q "Alice"; then
    echo -e "${GREEN}✓ 정상 검색 성공 (Alice 사용자 조회됨)${NC}"
else
    echo -e "${RED}✗ 정상 검색 실패${NC}"
fi
echo ""

# SQL Injection 공격 1: OR '1'='1 (모든 데이터 노출)
echo -e "${BLUE}[3/6] SQL Injection 공격 1: OR '1'='1${NC}"
echo "공격 패턴: ' OR '1'='1"
ATTACK1_URL="$VULNERABLE_URL/search-vulnerable?q=%27%20OR%20%271%27%3D%271"
RESPONSE=$(curl -s "$ATTACK1_URL")

# 실행된 쿼리 추출
if echo "$RESPONSE" | grep -q "실행된 쿼리:"; then
    QUERY=$(echo "$RESPONSE" | grep -oP "실행된 쿼리: \K[^<]+")
    echo -e "${YELLOW}실행된 쿼리: $QUERY${NC}"
fi

# 결과 확인 (여러 사용자가 조회되었는지 확인)
COUNT=$(echo "$RESPONSE" | grep -o "<tr>" | wc -l)
if [ "$COUNT" -gt 3 ]; then
    echo -e "${RED}✗ 공격 성공! 모든 사용자 정보 노출됨 (${COUNT}개 행)${NC}"
    echo -e "${YELLOW}위험: 조건을 우회하여 모든 데이터에 접근했습니다!${NC}"
else
    echo -e "${GREEN}✓ 공격 차단됨${NC}"
fi
echo ""

# SQL Injection 공격 2: UNION SELECT (데이터베이스 구조 탐색)
echo -e "${BLUE}[4/6] SQL Injection 공격 2: UNION SELECT${NC}"
echo "공격 패턴: ' UNION SELECT id, name, email FROM users--"
ATTACK2_URL="$VULNERABLE_URL/search-vulnerable?q=%27%20UNION%20SELECT%20id%2C%20name%2C%20email%20FROM%20users--"
RESPONSE=$(curl -s "$ATTACK2_URL")

# 실행된 쿼리 추출
if echo "$RESPONSE" | grep -q "실행된 쿼리:"; then
    QUERY=$(echo "$RESPONSE" | grep -oP "실행된 쿼리: \K[^<]+")
    echo -e "${YELLOW}실행된 쿼리: $QUERY${NC}"
fi

# 결과 확인
COUNT=$(echo "$RESPONSE" | grep -o "<tr>" | wc -l)
if [ "$COUNT" -gt 3 ]; then
    echo -e "${RED}✗ 공격 성공! UNION SELECT로 데이터 탈취됨 (${COUNT}개 행)${NC}"
    echo -e "${YELLOW}위험: 다른 테이블의 데이터까지 접근했습니다!${NC}"
else
    echo -e "${GREEN}✓ 공격 차단됨${NC}"
fi
echo ""

# 안전한 버전 테스트
echo -e "${BLUE}[5/6] 안전한 버전 테스트 (Prepared Statement)${NC}"
echo "공격 패턴: ' OR '1'='1 (동일한 공격)"
SAFE_URL="$VULNERABLE_URL/search-safe?q=%27%20OR%20%271%27%3D%271"
RESPONSE=$(curl -s "$SAFE_URL")

# 결과 확인
if echo "$RESPONSE" | grep -q "검색 결과가 없습니다"; then
    echo -e "${GREEN}✓ 공격 차단 성공! (Prepared Statement)${NC}"
    echo -e "${GREEN}  → 사용자 입력이 문자열로만 처리되어 SQL 코드로 실행되지 않음${NC}"
else
    COUNT=$(echo "$RESPONSE" | grep -o "<tr>" | wc -l)
    if [ "$COUNT" -gt 3 ]; then
        echo -e "${RED}✗ 안전한 버전도 뚫렸습니다!${NC}"
    else
        echo -e "${GREEN}✓ 공격 차단됨${NC}"
    fi
fi
echo ""

# XSS 공격 테스트
echo -e "${BLUE}[6/6] XSS 공격 테스트${NC}"
echo "공격 패턴: <script>alert('XSS')</script>"

# 취약한 버전
echo -e "${YELLOW}취약한 버전 (/greet-vulnerable):${NC}"
RESPONSE=$(curl -s -X POST "$VULNERABLE_URL/greet-vulnerable" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=<script>alert('XSS')</script>")

if echo "$RESPONSE" | grep -q "<script>alert('XSS')</script>"; then
    echo -e "${RED}✗ XSS 공격 성공! 스크립트가 그대로 삽입됨${NC}"
    echo -e "${YELLOW}위험: 브라우저에서 실행 시 악성 코드가 실행됩니다!${NC}"
else
    echo -e "${GREEN}✓ XSS 차단됨${NC}"
fi

# 안전한 버전
echo -e "${YELLOW}안전한 버전 (/greet-safe):${NC}"
RESPONSE=$(curl -s -X POST "$VULNERABLE_URL/greet-safe" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=<script>alert('XSS')</script>")

if echo "$RESPONSE" | grep -q "&lt;script&gt;"; then
    echo -e "${GREEN}✓ XSS 차단 성공! (HTML 이스케이프)${NC}"
    echo -e "${GREEN}  → <script> → &lt;script&gt; 변환됨${NC}"
else
    if echo "$RESPONSE" | grep -q "<script>alert('XSS')</script>"; then
        echo -e "${RED}✗ 안전한 버전도 뚫렸습니다!${NC}"
    else
        echo -e "${GREEN}✓ XSS 차단됨${NC}"
    fi
fi
echo ""

# 요약
echo "=================================================="
echo -e "${YELLOW}  테스트 요약${NC}"
echo "=================================================="
echo ""
echo "공격 패턴:"
echo "  1. OR '1'='1          - 조건 우회, 모든 데이터 노출"
echo "  2. UNION SELECT       - 다른 테이블 데이터 탈취"
echo "  3. <script> 태그      - XSS 공격"
echo ""
echo "방어 방법:"
echo "  1. Prepared Statement - SQL Injection 방어"
echo "  2. HTML 이스케이프    - XSS 방어"
echo ""
echo -e "${GREEN}테스트 완료!${NC}"
echo ""
