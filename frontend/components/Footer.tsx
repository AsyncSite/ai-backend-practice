'use client';

import { useEffect, useState } from 'react';
import { getServerInfo, type ServerInfo } from '@/lib/api';

export default function Footer() {
  const [serverInfo, setServerInfo] = useState<ServerInfo | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    getServerInfo()
      .then((res) => {
        if (res.success) setServerInfo(res.data);
      })
      .catch(() => setError(true));
  }, []);

  return (
    <footer className="bg-gray-50 border-t border-gray-200 mt-auto">
      <div className="max-w-6xl mx-auto px-4 py-8">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
          {/* Brand */}
          <div>
            <div className="flex items-center gap-2 font-bold text-lg text-blue-600 mb-1">
              <span>🛒</span>
              <span>GritShop</span>
            </div>
            <p className="text-xs text-gray-500">
              백엔드 학습용 한국 음식 주문 서비스
            </p>
          </div>

          {/* Dev links */}
          <div className="flex flex-wrap gap-3">
            <a
              href="http://localhost:8080/swagger-ui.html"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 bg-green-100 text-green-700 rounded-full hover:bg-green-200 transition-colors font-medium"
            >
              <span>📄</span>
              <span>Swagger UI</span>
            </a>
            <a
              href="http://localhost:3001"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 bg-orange-100 text-orange-700 rounded-full hover:bg-orange-200 transition-colors font-medium"
            >
              <span>📊</span>
              <span>Grafana</span>
            </a>
            <a
              href="http://localhost:15672"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 bg-purple-100 text-purple-700 rounded-full hover:bg-purple-200 transition-colors font-medium"
            >
              <span>🐰</span>
              <span>RabbitMQ</span>
            </a>
            <a
              href="http://localhost:9090"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 bg-red-100 text-red-700 rounded-full hover:bg-red-200 transition-colors font-medium"
            >
              <span>🔥</span>
              <span>Prometheus</span>
            </a>
          </div>
        </div>

        {/* Server info badge */}
        <div className="mt-6 pt-4 border-t border-gray-200">
          {serverInfo ? (
            <div className="flex flex-wrap items-center gap-4 text-xs text-gray-500">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse inline-block"></span>
                <span>서버 연결됨</span>
              </span>
              <span>
                서버 ID:{' '}
                <code className="bg-gray-100 px-1.5 py-0.5 rounded font-mono text-gray-700">
                  {serverInfo.serverId}
                </code>
              </span>
              <span>
                호스트:{' '}
                <code className="bg-gray-100 px-1.5 py-0.5 rounded font-mono text-gray-700">
                  {serverInfo.hostname}
                </code>
              </span>
              {serverInfo.version && (
                <span>
                  버전:{' '}
                  <code className="bg-gray-100 px-1.5 py-0.5 rounded font-mono text-gray-700">
                    {serverInfo.version}
                  </code>
                </span>
              )}
            </div>
          ) : error ? (
            <div className="flex items-center gap-2 text-xs text-red-500">
              <span className="w-2 h-2 rounded-full bg-red-400 inline-block"></span>
              <span>백엔드 연결 실패 - 서버 정보를 가져올 수 없습니다</span>
            </div>
          ) : (
            <div className="flex items-center gap-2 text-xs text-gray-400">
              <span className="w-2 h-2 rounded-full bg-gray-300 animate-pulse inline-block"></span>
              <span>서버 정보 로딩 중...</span>
            </div>
          )}
        </div>
      </div>
    </footer>
  );
}
