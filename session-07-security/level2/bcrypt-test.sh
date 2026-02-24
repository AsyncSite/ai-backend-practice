#!/bin/bash

# [Session 07 - Level 2] BCrypt 해싱 자동화 테스트 스크립트
#
# 이 스크립트는 BCryptPasswordEncoder의 동작을 자동으로 테스트합니다.
# Spring Boot 앱(http://localhost:8080)이 실행 중이어야 합니다.

set -e  # 에러 발생 시 스크립트 중단

# 색상 출력 설정
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

API_URL="http://localhost:8080/api/auth"

echo "=================================================="
echo "  BCrypt 해싱 자동화 테스트"
echo "=================================================="
echo ""

# 서버 상태 확인
echo -e "${BLUE}[1/5] 서버 상태 확인${NC}"
if curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/actuator/health" | grep -q "200"; then
    echo -e "${GREEN}✓ Spring Boot 앱 서버 실행 중 (http://localhost:8080)${NC}"
else
    echo -e "${RED}✗ 서버가 응답하지 않습니다.${NC}"
    echo -e "${YELLOW}힌트: 'docker compose up -d app' 실행하세요.${NC}"
    exit 1
fi
echo ""

# BCrypt 해싱 테스트 (같은 비밀번호, 다른 해시값)
echo -e "${BLUE}[2/5] BCrypt 해싱 테스트 - 같은 비밀번호, 다른 해시값${NC}"
PASSWORD="test123"

echo "비밀번호: $PASSWORD"
echo ""

# 첫 번째 해싱
echo -e "${YELLOW}첫 번째 해싱:${NC}"
HASH1=$(curl -s "$API_URL/hash?password=$PASSWORD")
echo "$HASH1"

# 두 번째 해싱
echo ""
echo -e "${YELLOW}두 번째 해싱:${NC}"
HASH2=$(curl -s "$API_URL/hash?password=$PASSWORD")
echo "$HASH2"

# 세 번째 해싱
echo ""
echo -e "${YELLOW}세 번째 해싱:${NC}"
HASH3=$(curl -s "$API_URL/hash?password=$PASSWORD")
echo "$HASH3"

echo ""
# 해시값이 다른지 확인
if [ "$HASH1" != "$HASH2" ] && [ "$HASH2" != "$HASH3" ]; then
    echo -e "${GREEN}✓ 성공: 같은 비밀번호라도 매번 다른 해시값 생성됨${NC}"
    echo -e "${GREEN}  → BCrypt는 자동으로 랜덤 솔트(salt)를 생성합니다${NC}"
else
    echo -e "${RED}✗ 실패: 해시값이 같습니다 (BCrypt가 제대로 작동하지 않음)${NC}"
fi
echo ""

# BCrypt 해시 구조 분석
echo -e "${BLUE}[3/5] BCrypt 해시 구조 분석${NC}"
echo "해시값: $HASH1"
echo ""
echo "구조:"
echo "  $(echo $HASH1 | cut -c1-4)     - 알고리즘 버전 (\$2a = BCrypt)"
echo "  $(echo $HASH1 | cut -c5-7)      - Cost Factor (2^10 = 1024 라운드)"
echo "  $(echo $HASH1 | cut -c8-29)  - 솔트 (22글자, 랜덤 생성)"
echo "  $(echo $HASH1 | cut -c30-)    - 실제 해시값 (31글자)"
echo ""

# BCrypt 검증 테스트 (올바른 비밀번호)
echo -e "${BLUE}[4/5] BCrypt 검증 테스트 - 올바른 비밀번호${NC}"
echo "해시값: $HASH1"
echo "비밀번호: $PASSWORD"

VERIFY_RESULT=$(curl -s -X POST "$API_URL/verify" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"$PASSWORD\",\"hash\":\"$HASH1\"}")

if echo "$VERIFY_RESULT" | grep -q '"valid":true'; then
    echo -e "${GREEN}✓ 검증 성공: 비밀번호가 일치합니다${NC}"
else
    echo -e "${RED}✗ 검증 실패: 비밀번호가 일치하지 않습니다${NC}"
    echo "응답: $VERIFY_RESULT"
fi
echo ""

# BCrypt 검증 테스트 (잘못된 비밀번호)
echo -e "${BLUE}[5/5] BCrypt 검증 테스트 - 잘못된 비밀번호${NC}"
WRONG_PASSWORD="wrong123"
echo "해시값: $HASH1"
echo "비밀번호: $WRONG_PASSWORD (잘못된 비밀번호)"

VERIFY_RESULT=$(curl -s -X POST "$API_URL/verify" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"$WRONG_PASSWORD\",\"hash\":\"$HASH1\"}")

if echo "$VERIFY_RESULT" | grep -q '"valid":false'; then
    echo -e "${GREEN}✓ 검증 성공: 잘못된 비밀번호를 올바르게 거부했습니다${NC}"
else
    echo -e "${RED}✗ 검증 실패: 잘못된 비밀번호를 허용했습니다${NC}"
    echo "응답: $VERIFY_RESULT"
fi
echo ""

# 다양한 비밀번호로 해싱 테스트
echo "=================================================="
echo -e "${YELLOW}  추가 테스트: 다양한 비밀번호${NC}"
echo "=================================================="
echo ""

PASSWORDS=("password" "admin123" "P@ssw0rd!" "가나다라" "12345678")

for PWD in "${PASSWORDS[@]}"; do
    echo -e "${YELLOW}비밀번호:${NC} $PWD"
    HASH=$(curl -s "$API_URL/hash?password=$(echo -n "$PWD" | jq -sRr @uri)")
    echo -e "${GREEN}해시값:${NC} $HASH"
    echo ""
done

# 요약
echo "=================================================="
echo -e "${YELLOW}  테스트 요약${NC}"
echo "=================================================="
echo ""
echo "BCrypt 특징:"
echo "  1. 같은 비밀번호도 매번 다른 해시값 생성 (랜덤 솔트)"
echo "  2. 단방향 해시 (해시값으로 원래 비밀번호 복구 불가)"
echo "  3. Cost Factor 조절 가능 (보안 강도 vs 속도)"
echo "  4. 레인보우 테이블 공격 방어 (솔트 덕분)"
echo ""
echo "BCrypt vs 일반 해시:"
echo "  MD5/SHA-1:  빠르지만 레인보우 테이블 공격에 취약"
echo "  BCrypt:     느리지만 안전 (Brute Force 공격 방어)"
echo ""
echo -e "${GREEN}테스트 완료!${NC}"
echo ""
