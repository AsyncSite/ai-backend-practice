#!/bin/bash

# [Session 09 - Level 2] DNS 조회 실습

echo "=================================="
echo "DNS 조회 실습"
echo "=================================="

echo ""
echo "1. nslookup으로 DNS 조회"
echo "----------------------------------"
nslookup google.com

echo ""
echo "2. dig로 상세 DNS 조회"
echo "----------------------------------"
dig google.com

echo ""
echo "3. A 레코드만 간단히 확인"
echo "----------------------------------"
dig google.com +short

echo ""
echo "4. 다양한 레코드 타입"
echo "----------------------------------"
echo "MX 레코드 (메일 서버):"
dig google.com MX +short

echo ""
echo "NS 레코드 (네임서버):"
dig google.com NS +short

echo ""
echo "5. RTT(왕복 시간) 측정"
echo "----------------------------------"
ping -c 5 google.com

echo ""
echo "=================================="
echo "💡 DNS 레코드 타입"
echo "=================================="
echo "A: IPv4 주소"
echo "AAAA: IPv6 주소"
echo "CNAME: 별칭"
echo "MX: 메일 서버"
echo "NS: 네임서버"
echo "TXT: 텍스트 정보"
