# Node.js 20 Alpine 기반 경량 이미지 사용
FROM node:20-alpine

# 작업 디렉토리 설정
WORKDIR /app

# package.json 복사 및 의존성 설치
COPY package.json .
RUN npm install --production

# 서버 코드 복사
COPY server.js .

# 9000번 포트 노출
EXPOSE 9000

# 서버 실행
CMD ["node", "server.js"]
