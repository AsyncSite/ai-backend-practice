#!/bin/bash

# [Session 11 - Level 1] Dockerfile 분석 스크립트

echo "=================================="
echo "Dockerfile 분석"
echo "=================================="

DOCKERFILE_PATH="../../../app/Dockerfile"

echo ""
echo "1. Dockerfile 내용"
echo "----------------------------------"
cat ${DOCKERFILE_PATH}

echo ""
echo ""
echo "2. 멀티스테이지 빌드 분석"
echo "----------------------------------"
echo "Stage 1 (builder): 소스 코드 빌드"
echo "  - Gradle로 JAR 파일 생성"
echo "  - 빌드 도구와 소스는 최종 이미지에 포함되지 않음"
echo ""
echo "Stage 2 (runtime): 실행 환경"
echo "  - 빌드된 JAR만 복사"
echo "  - 경량 JRE 이미지 사용"
echo "  - 최종 이미지 크기 최소화"

echo ""
echo "3. 이미지 빌드"
echo "----------------------------------"
cd ../../../app
docker build -t backend-practice:latest .

echo ""
echo "4. 이미지 크기 확인"
echo "----------------------------------"
docker images backend-practice

echo ""
echo "=================================="
echo "💡 멀티스테이지 빌드의 장점"
echo "=================================="
echo "- 최종 이미지 크기 감소 (빌드 도구 제외)"
echo "- 보안 향상 (소스 코드 미포함)"
echo "- 레이어 캐싱으로 빌드 속도 개선"
