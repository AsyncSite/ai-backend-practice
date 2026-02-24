FROM node:20-alpine

WORKDIR /app

# 패키지 파일 복사 및 의존성 설치
COPY package*.json ./
RUN npm install --production

# 애플리케이션 소스 복사
COPY server.js ./

# 포트 노출
EXPOSE 9999

# 애플리케이션 실행
CMD ["node", "server.js"]
