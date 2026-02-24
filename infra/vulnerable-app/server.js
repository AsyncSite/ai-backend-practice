// ⚠️⚠️⚠️ 경고: 교육 목적 전용 ⚠️⚠️⚠️
// 이 코드는 보안 취약점을 학습하기 위한 예제입니다.
// 절대 실제 프로덕션 환경에서 사용하지 마세요!

const express = require('express');
const mysql = require('mysql2');

const app = express();
const PORT = 9999;

// 환경 변수에서 DB 설정 읽기
const dbConfig = {
  host: process.env.DB_HOST || 'mysql',
  port: process.env.DB_PORT || 3306,
  database: process.env.DB_NAME || 'backend_study',
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || 'root1234'
};

// MySQL 연결 풀 생성
const pool = mysql.createPool(dbConfig);
const promisePool = pool.promise();

// 미들웨어
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// HTML 엔티티 이스케이프 함수 (안전한 버전용)
function escapeHtml(text) {
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.replace(/[&<>"']/g, (m) => map[m]);
}

// 메인 페이지
app.get('/', (req, res) => {
  res.send(`
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>보안 취약점 학습 실습 환경</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      min-height: 100vh;
      padding: 20px;
    }
    .container {
      max-width: 900px;
      margin: 0 auto;
    }
    .warning-banner {
      background: #ff4444;
      color: white;
      padding: 20px;
      border-radius: 10px;
      margin-bottom: 30px;
      text-align: center;
      font-size: 18px;
      font-weight: bold;
      box-shadow: 0 4px 15px rgba(0,0,0,0.2);
      animation: pulse 2s infinite;
    }
    @keyframes pulse {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.02); }
    }
    .section {
      background: white;
      padding: 25px;
      border-radius: 12px;
      margin-bottom: 25px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
    }
    .section-title {
      font-size: 24px;
      margin-bottom: 15px;
      color: #333;
      border-bottom: 3px solid #667eea;
      padding-bottom: 10px;
    }
    .exercise-box {
      background: #f8f9fa;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 15px;
      border-left: 4px solid #667eea;
    }
    .exercise-box.vulnerable {
      border-left-color: #ff4444;
      background: #fff5f5;
    }
    .exercise-box.safe {
      border-left-color: #00c851;
      background: #f5fff5;
    }
    .label {
      font-weight: bold;
      font-size: 18px;
      margin-bottom: 10px;
      display: flex;
      align-items: center;
    }
    .badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      margin-left: 10px;
      font-weight: bold;
    }
    .badge.vulnerable {
      background: #ff4444;
      color: white;
    }
    .badge.safe {
      background: #00c851;
      color: white;
    }
    .description {
      color: #666;
      margin-bottom: 15px;
      line-height: 1.6;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    input[type="text"] {
      padding: 12px;
      border: 2px solid #ddd;
      border-radius: 6px;
      font-size: 16px;
      transition: border-color 0.3s;
    }
    input[type="text"]:focus {
      outline: none;
      border-color: #667eea;
    }
    button {
      padding: 12px 24px;
      border: none;
      border-radius: 6px;
      font-size: 16px;
      cursor: pointer;
      font-weight: bold;
      transition: all 0.3s;
    }
    .btn-vulnerable {
      background: #ff4444;
      color: white;
    }
    .btn-vulnerable:hover {
      background: #cc0000;
      transform: translateY(-2px);
      box-shadow: 0 4px 10px rgba(255,68,68,0.3);
    }
    .btn-safe {
      background: #00c851;
      color: white;
    }
    .btn-safe:hover {
      background: #007e33;
      transform: translateY(-2px);
      box-shadow: 0 4px 10px rgba(0,200,81,0.3);
    }
    .info-box {
      background: #e3f2fd;
      border-left: 4px solid #2196f3;
      padding: 15px;
      border-radius: 6px;
      margin-top: 10px;
    }
    .info-box strong {
      color: #1976d2;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="warning-banner">
      ⚠️ 교육 목적 전용 - 보안 취약점 학습 실습 환경 ⚠️
      <div style="font-size: 14px; margin-top: 10px; font-weight: normal;">
        이 애플리케이션은 의도적으로 보안 취약점을 포함하고 있습니다.<br>
        절대 실제 서비스에 이런 코드를 사용하지 마세요!
      </div>
    </div>

    <!-- SQL Injection 섹션 -->
    <div class="section">
      <div class="section-title">🔍 SQL Injection 실습</div>

      <div class="exercise-box vulnerable">
        <div class="label">
          검색 (취약한 버전)
          <span class="badge vulnerable">VULNERABLE</span>
        </div>
        <div class="description">
          ⚠️ 이 엔드포인트는 SQL 문자열 결합을 사용하여 SQL Injection에 취약합니다.<br>
          사용자 입력을 직접 쿼리에 삽입하므로 악의적인 SQL 코드를 실행할 수 있습니다.
        </div>
        <form action="/search-vulnerable" method="GET">
          <input type="text" name="q" placeholder="이름을 검색하세요 (예: ' OR '1'='1)" required>
          <button type="submit" class="btn-vulnerable">취약한 검색 실행</button>
        </form>
        <div class="info-box">
          <strong>시도해보기:</strong> <code>' OR '1'='1</code> 또는 <code>' UNION SELECT id, name, email FROM users--</code>
        </div>
      </div>

      <div class="exercise-box safe">
        <div class="label">
          검색 (안전한 버전)
          <span class="badge safe">SAFE</span>
        </div>
        <div class="description">
          ✅ 이 엔드포인트는 Prepared Statement를 사용하여 SQL Injection을 방지합니다.<br>
          사용자 입력이 매개변수로 처리되어 SQL 코드로 해석되지 않습니다.
        </div>
        <form action="/search-safe" method="GET">
          <input type="text" name="q" placeholder="검색어를 입력하세요" required>
          <button type="submit" class="btn-safe">안전한 검색 실행</button>
        </form>
        <div class="info-box">
          <strong>확인:</strong> 같은 악의적인 입력을 시도해도 안전하게 처리됩니다.
        </div>
      </div>
    </div>

    <!-- XSS 섹션 -->
    <div class="section">
      <div class="section-title">💉 Cross-Site Scripting (XSS) 실습</div>

      <div class="exercise-box vulnerable">
        <div class="label">
          인사 메시지 (취약한 버전)
          <span class="badge vulnerable">VULNERABLE</span>
        </div>
        <div class="description">
          ⚠️ 이 엔드포인트는 사용자 입력을 HTML 이스케이프 없이 그대로 출력합니다.<br>
          악의적인 JavaScript 코드를 주입하여 실행할 수 있습니다.
        </div>
        <form action="/greet-vulnerable" method="POST">
          <input type="text" name="name" placeholder="이름을 입력하세요 (예: <script>alert('XSS')</script>)" required>
          <button type="submit" class="btn-vulnerable">취약한 인사 실행</button>
        </form>
        <div class="info-box">
          <strong>시도해보기:</strong> <code>&lt;script&gt;alert('XSS 공격!')&lt;/script&gt;</code> 또는 <code>&lt;img src=x onerror=alert('XSS')&gt;</code>
        </div>
      </div>

      <div class="exercise-box safe">
        <div class="label">
          인사 메시지 (안전한 버전)
          <span class="badge safe">SAFE</span>
        </div>
        <div class="description">
          ✅ 이 엔드포인트는 HTML 엔티티 이스케이프를 적용하여 XSS를 방지합니다.<br>
          사용자 입력의 특수 문자가 안전하게 변환되어 스크립트로 실행되지 않습니다.
        </div>
        <form action="/greet-safe" method="POST">
          <input type="text" name="name" placeholder="이름을 입력하세요" required>
          <button type="submit" class="btn-safe">안전한 인사 실행</button>
        </form>
        <div class="info-box">
          <strong>확인:</strong> 같은 악의적인 입력을 시도해도 안전하게 텍스트로만 표시됩니다.
        </div>
      </div>
    </div>
  </div>
</body>
</html>
  `);
});

// ❌ 취약한 검색 엔드포인트 - SQL Injection 가능
// 절대 이렇게 코드를 작성하지 마세요!
app.get('/search-vulnerable', async (req, res) => {
  const searchQuery = req.query.q || '';

  // ⚠️ 위험: 문자열 결합으로 SQL 쿼리 생성 (SQL Injection 취약점)
  const sql = `SELECT id, name, email FROM users WHERE name LIKE '%${searchQuery}%'`;

  try {
    const [rows] = await promisePool.query(sql);

    res.send(`
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>검색 결과 (취약한 버전)</title>
  <style>
    body { font-family: Arial, sans-serif; padding: 20px; background: #fff5f5; }
    .warning { background: #ff4444; color: white; padding: 15px; border-radius: 8px; margin-bottom: 20px; }
    .results { background: white; padding: 20px; border-radius: 8px; border: 2px solid #ff4444; }
    table { width: 100%; border-collapse: collapse; margin-top: 15px; }
    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
    th { background: #ff4444; color: white; }
    a { display: inline-block; margin-top: 20px; color: #667eea; text-decoration: none; }
  </style>
</head>
<body>
  <div class="warning">
    ⚠️ 취약한 검색 결과 - SQL Injection 취약점 포함<br>
    <small>실행된 쿼리: ${sql}</small>
  </div>
  <div class="results">
    <h2>검색 결과 (${rows.length}건)</h2>
    ${rows.length > 0 ? `
      <table>
        <tr>
          <th>ID</th>
          <th>Name</th>
          <th>Email</th>
        </tr>
        ${rows.map(row => `
          <tr>
            <td>${row.id}</td>
            <td>${row.name}</td>
            <td>${row.email || 'N/A'}</td>
          </tr>
        `).join('')}
      </table>
    ` : '<p>검색 결과가 없습니다.</p>'}
  </div>
  <a href="/">← 돌아가기</a>
</body>
</html>
    `);
  } catch (error) {
    res.status(500).send(`
      <h1>에러 발생</h1>
      <p style="color: red;">SQL 에러: ${error.message}</p>
      <p>쿼리: ${sql}</p>
      <a href="/">← 돌아가기</a>
    `);
  }
});

// ✅ 안전한 검색 엔드포인트 - Prepared Statement 사용
app.get('/search-safe', async (req, res) => {
  const searchQuery = req.query.q || '';

  // ✅ 안전: Prepared Statement 사용 (SQL Injection 방지)
  const sql = 'SELECT id, name, email FROM users WHERE name LIKE ?';
  const params = [`%${searchQuery}%`];

  try {
    const [rows] = await promisePool.query(sql, params);

    res.send(`
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>검색 결과 (안전한 버전)</title>
  <style>
    body { font-family: Arial, sans-serif; padding: 20px; background: #f5fff5; }
    .success { background: #00c851; color: white; padding: 15px; border-radius: 8px; margin-bottom: 20px; }
    .results { background: white; padding: 20px; border-radius: 8px; border: 2px solid #00c851; }
    table { width: 100%; border-collapse: collapse; margin-top: 15px; }
    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
    th { background: #00c851; color: white; }
    a { display: inline-block; margin-top: 20px; color: #667eea; text-decoration: none; }
  </style>
</head>
<body>
  <div class="success">
    ✅ 안전한 검색 결과 - Prepared Statement 사용<br>
    <small>매개변수화된 쿼리로 SQL Injection 방지</small>
  </div>
  <div class="results">
    <h2>검색 결과 (${rows.length}건)</h2>
    ${rows.length > 0 ? `
      <table>
        <tr>
          <th>ID</th>
          <th>Name</th>
          <th>Email</th>
        </tr>
        ${rows.map(row => `
          <tr>
            <td>${row.id}</td>
            <td>${escapeHtml(row.name || '')}</td>
            <td>${escapeHtml(row.email || 'N/A')}</td>
          </tr>
        `).join('')}
      </table>
    ` : '<p>검색 결과가 없습니다.</p>'}
  </div>
  <a href="/">← 돌아가기</a>
</body>
</html>
    `);
  } catch (error) {
    res.status(500).send(`
      <h1>에러 발생</h1>
      <p style="color: red;">에러: ${escapeHtml(error.message)}</p>
      <a href="/">← 돌아가기</a>
    `);
  }
});

// ❌ 취약한 인사 엔드포인트 - XSS 가능
// 절대 이렇게 코드를 작성하지 마세요!
app.post('/greet-vulnerable', (req, res) => {
  const name = req.body.name || '';

  // ⚠️ 위험: HTML 이스케이프 없이 사용자 입력을 직접 출력 (XSS 취약점)
  res.send(`
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>인사 결과 (취약한 버전)</title>
  <style>
    body { font-family: Arial, sans-serif; padding: 20px; background: #fff5f5; }
    .warning { background: #ff4444; color: white; padding: 15px; border-radius: 8px; margin-bottom: 20px; }
    .greeting { background: white; padding: 20px; border-radius: 8px; border: 2px solid #ff4444; font-size: 24px; }
    a { display: inline-block; margin-top: 20px; color: #667eea; text-decoration: none; }
  </style>
</head>
<body>
  <div class="warning">
    ⚠️ 취약한 인사 메시지 - XSS 취약점 포함<br>
    <small>사용자 입력이 이스케이프 없이 출력됩니다</small>
  </div>
  <div class="greeting">
    안녕하세요, ${name}님!
  </div>
  <a href="/">← 돌아가기</a>
</body>
</html>
  `);
});

// ✅ 안전한 인사 엔드포인트 - HTML 이스케이프 사용
app.post('/greet-safe', (req, res) => {
  const name = req.body.name || '';

  // ✅ 안전: HTML 엔티티 이스케이프 적용 (XSS 방지)
  const safeName = escapeHtml(name);

  res.send(`
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>인사 결과 (안전한 버전)</title>
  <style>
    body { font-family: Arial, sans-serif; padding: 20px; background: #f5fff5; }
    .success { background: #00c851; color: white; padding: 15px; border-radius: 8px; margin-bottom: 20px; }
    .greeting { background: white; padding: 20px; border-radius: 8px; border: 2px solid #00c851; font-size: 24px; }
    a { display: inline-block; margin-top: 20px; color: #667eea; text-decoration: none; }
  </style>
</head>
<body>
  <div class="success">
    ✅ 안전한 인사 메시지 - HTML 이스케이프 적용<br>
    <small>사용자 입력의 특수 문자가 안전하게 변환됩니다</small>
  </div>
  <div class="greeting">
    안녕하세요, ${safeName}님!
  </div>
  <a href="/">← 돌아가기</a>
</body>
</html>
  `);
});

// 서버 시작
app.listen(PORT, () => {
  console.log(`⚠️  보안 취약점 학습 서버가 포트 ${PORT}에서 실행 중입니다`);
  console.log(`⚠️  교육 목적 전용 - 실제 서비스에 사용하지 마세요!`);
  console.log(`DB 설정: ${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);
});
