const express = require('express');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = 9000;

// 환경변수에서 실패율과 최대 지연시간 읽기 (기본값 설정)
const FAILURE_RATE = parseFloat(process.env.FAILURE_RATE || '0.2');
const MAX_DELAY_MS = parseInt(process.env.MAX_DELAY_MS || '3000', 10);

// JSON 요청 바디 파싱
app.use(express.json());

// 헬스체크 엔드포인트
app.get('/health', (req, res) => {
  res.json({ status: 'UP' });
});

// 결제 처리 엔드포인트
app.post('/api/payments', async (req, res) => {
  const { amount, orderId } = req.body;

  // 요청 로깅
  console.log(`[PG 요청] orderId: ${orderId}, amount: ${amount}`);

  // 랜덤 지연 시간 생성 (0 ~ MAX_DELAY_MS)
  const delay = Math.floor(Math.random() * MAX_DELAY_MS);

  // 성공/실패 랜덤 결정
  const shouldFail = Math.random() < FAILURE_RATE;

  // 지연 시뮬레이션
  await new Promise(resolve => setTimeout(resolve, delay));

  if (shouldFail) {
    // 실패 응답
    console.log(`[PG 실패] orderId: ${orderId}, delay: ${delay}ms`);
    return res.status(500).json({
      status: 'FAILED',
      error: 'PG 결제 실패'
    });
  }

  // 성공 응답
  const transactionId = uuidv4();
  console.log(`[PG 성공] orderId: ${orderId}, transactionId: ${transactionId}, delay: ${delay}ms`);

  res.json({
    transactionId,
    status: 'SUCCESS',
    amount
  });
});

// 서버 시작
app.listen(PORT, () => {
  console.log(`Mock PG 서비스가 포트 ${PORT}에서 실행 중입니다.`);
  console.log(`실패율: ${FAILURE_RATE * 100}%`);
  console.log(`최대 지연시간: ${MAX_DELAY_MS}ms`);
});
