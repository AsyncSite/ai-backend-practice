'use client';

import { useState, useRef } from 'react';
import { decreaseStock } from '@/lib/api';

interface RequestResult {
  id: number;
  useLock: boolean;
  status: 'pending' | 'success' | 'error';
  stock?: number;
  error?: string;
  duration?: number;
}

const MENU_ID_DEFAULT = 1;

export default function StockDemoPage() {
  const [menuId, setMenuId] = useState(MENU_ID_DEFAULT);
  const [concurrency, setConcurrency] = useState(10);
  const [results, setResults] = useState<RequestResult[]>([]);
  const [running, setRunning] = useState(false);
  const counterRef = useRef(0);

  function addResult(result: RequestResult) {
    setResults((prev) => [result, ...prev].slice(0, 100));
  }

  async function runRequests(useLock: boolean) {
    setRunning(true);
    const id = ++counterRef.current;
    const batchId = id;

    const requestIds = Array.from({ length: concurrency }, (_, i) => batchId * 1000 + i);

    // Initialize all as pending
    setResults((prev) => [
      ...requestIds.map((rid) => ({
        id: rid,
        useLock,
        status: 'pending' as const,
      })),
      ...prev,
    ].slice(0, 100));

    // Fire all concurrently
    const promises = requestIds.map(async (rid, i) => {
      const start = performance.now();
      try {
        const res = await decreaseStock(menuId, useLock);
        const duration = Math.round(performance.now() - start);
        const result: RequestResult = {
          id: rid,
          useLock,
          status: 'success',
          stock: res.data?.stock,
          duration,
        };
        setResults((prev) =>
          prev.map((r) => (r.id === rid ? result : r))
        );
      } catch (e) {
        const duration = Math.round(performance.now() - start);
        const result: RequestResult = {
          id: rid,
          useLock,
          status: 'error',
          error: e instanceof Error ? e.message : '오류 발생',
          duration,
        };
        setResults((prev) =>
          prev.map((r) => (r.id === rid ? result : r))
        );
      }
    });

    await Promise.all(promises);
    setRunning(false);
  }

  function clearResults() {
    setResults([]);
  }

  const successCount = results.filter((r) => r.status === 'success').length;
  const errorCount = results.filter((r) => r.status === 'error').length;
  const pendingCount = results.filter((r) => r.status === 'pending').length;

  const stockValues = results
    .filter((r) => r.status === 'success' && r.stock !== undefined)
    .map((r) => r.stock as number);
  const minStock = stockValues.length > 0 ? Math.min(...stockValues) : null;
  const maxStock = stockValues.length > 0 ? Math.max(...stockValues) : null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-extrabold text-gray-900 mb-2">
          동시성 제어 데모
        </h1>
        <p className="text-gray-500">
          여러 요청을 동시에 보내 재고 감소 API의 동시성 처리를 테스트합니다.
          락(Lock) 사용 여부에 따른 차이를 직접 확인해보세요.
        </p>
      </div>

      {/* Concept explanation */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
          <h3 className="font-bold text-red-700 mb-2">락 없음 (Race Condition 위험)</h3>
          <p className="text-sm text-red-600">
            여러 요청이 동시에 같은 재고를 읽고 감소시킵니다.
            경쟁 조건(Race Condition)이 발생하면 재고가 실제보다 많이
            남거나 음수가 될 수 있습니다.
          </p>
          <div className="mt-3 font-mono text-xs bg-red-100 p-2 rounded text-red-700">
            T1: read(stock=10) → write(9)<br />
            T2: read(stock=10) → write(9) ← 버그!
          </div>
        </div>
        <div className="bg-green-50 border border-green-200 rounded-xl p-4">
          <h3 className="font-bold text-green-700 mb-2">락 있음 (안전)</h3>
          <p className="text-sm text-green-600">
            분산 락 또는 DB 락을 사용하면 한 번에 한 요청만 재고를 처리합니다.
            순차적으로 정확하게 처리되어 재고 정합성이 보장됩니다.
          </p>
          <div className="mt-3 font-mono text-xs bg-green-100 p-2 rounded text-green-700">
            T1: lock → read(10) → write(9) → unlock<br />
            T2: wait... → lock → read(9) → write(8)
          </div>
        </div>
      </div>

      {/* Config */}
      <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-6">
        <h2 className="font-bold text-gray-900 mb-4">테스트 설정</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              메뉴 ID
            </label>
            <input
              type="number"
              min={1}
              value={menuId}
              onChange={(e) => setMenuId(Number(e.target.value))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              동시 요청 수
              <span className="ml-2 font-normal text-gray-400">
                (현재: {concurrency})
              </span>
            </label>
            <input
              type="range"
              min={1}
              max={50}
              value={concurrency}
              onChange={(e) => setConcurrency(Number(e.target.value))}
              className="w-full accent-blue-600"
            />
            <div className="flex justify-between text-xs text-gray-400 mt-1">
              <span>1</span>
              <span>25</span>
              <span>50</span>
            </div>
          </div>
        </div>

        {/* Action buttons */}
        <div className="flex flex-wrap gap-3">
          <button
            onClick={() => runRequests(false)}
            disabled={running}
            className="flex-1 sm:flex-none px-6 py-3 bg-red-500 text-white font-bold rounded-xl hover:bg-red-600 disabled:bg-gray-200 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
          >
            {running ? (
              <span className="flex items-center gap-2 justify-center">
                <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin"></span>
                실행 중...
              </span>
            ) : (
              `락 없이 ${concurrency}개 동시 요청`
            )}
          </button>

          <button
            onClick={() => runRequests(true)}
            disabled={running}
            className="flex-1 sm:flex-none px-6 py-3 bg-green-600 text-white font-bold rounded-xl hover:bg-green-700 disabled:bg-gray-200 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
          >
            {running ? (
              <span className="flex items-center gap-2 justify-center">
                <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin"></span>
                실행 중...
              </span>
            ) : (
              `락 사용 ${concurrency}개 동시 요청`
            )}
          </button>

          {results.length > 0 && (
            <button
              onClick={clearResults}
              disabled={running}
              className="px-4 py-3 border border-gray-300 text-gray-600 rounded-xl hover:bg-gray-50 transition-colors font-medium"
            >
              초기화
            </button>
          )}
        </div>
      </div>

      {/* Stats */}
      {results.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5">
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center">
            <div className="text-2xl font-extrabold text-blue-600">{results.length}</div>
            <div className="text-xs text-gray-500 mt-1">총 요청</div>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center">
            <div className="text-2xl font-extrabold text-green-600">{successCount}</div>
            <div className="text-xs text-gray-500 mt-1">성공</div>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center">
            <div className="text-2xl font-extrabold text-red-500">{errorCount}</div>
            <div className="text-xs text-gray-500 mt-1">실패</div>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center">
            <div className="text-2xl font-extrabold text-yellow-600">{pendingCount}</div>
            <div className="text-xs text-gray-500 mt-1">대기 중</div>
          </div>
        </div>
      )}

      {minStock !== null && maxStock !== null && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-5">
          <p className="text-sm font-semibold text-amber-700 mb-1">재고 분석</p>
          <p className="text-sm text-amber-600">
            응답된 재고 범위:{' '}
            <strong>{minStock}</strong> ~ <strong>{maxStock}</strong>
            {minStock !== maxStock && (
              <span className="ml-2 text-amber-500">
                (서로 다른 재고값이 반환됨 - 동시성 이슈 가능성)
              </span>
            )}
          </p>
        </div>
      )}

      {/* Results list */}
      {results.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
          <div className="px-5 py-3.5 border-b border-gray-100 flex items-center justify-between">
            <h3 className="font-bold text-gray-900">요청 결과</h3>
            <span className="text-xs text-gray-400">최근 100개</span>
          </div>
          <div className="divide-y divide-gray-50 max-h-96 overflow-y-auto">
            {results.map((result) => (
              <div
                key={result.id}
                className={`flex items-center justify-between px-5 py-3 text-sm ${
                  result.status === 'pending' ? 'bg-gray-50' : ''
                }`}
              >
                <div className="flex items-center gap-3">
                  {result.status === 'pending' && (
                    <span className="w-2 h-2 rounded-full bg-gray-300 animate-pulse flex-shrink-0"></span>
                  )}
                  {result.status === 'success' && (
                    <span className="w-2 h-2 rounded-full bg-green-400 flex-shrink-0"></span>
                  )}
                  {result.status === 'error' && (
                    <span className="w-2 h-2 rounded-full bg-red-400 flex-shrink-0"></span>
                  )}
                  <span
                    className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                      result.useLock
                        ? 'bg-green-100 text-green-700'
                        : 'bg-red-100 text-red-700'
                    }`}
                  >
                    {result.useLock ? '락 사용' : '락 없음'}
                  </span>
                  {result.status === 'success' && result.stock !== undefined && (
                    <span className="text-gray-700">
                      재고:{' '}
                      <strong className="text-gray-900">{result.stock}</strong>
                    </span>
                  )}
                  {result.status === 'error' && (
                    <span className="text-red-500 truncate max-w-xs">
                      {result.error}
                    </span>
                  )}
                  {result.status === 'pending' && (
                    <span className="text-gray-400">처리 중...</span>
                  )}
                </div>
                {result.duration !== undefined && (
                  <span className="text-xs text-gray-400 flex-shrink-0">
                    {result.duration}ms
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {results.length === 0 && (
        <div className="text-center py-16 text-gray-400 bg-white rounded-2xl border border-gray-200">
          <span className="text-4xl block mb-3">🔬</span>
          <p>위 버튼을 클릭하여 동시성 테스트를 시작하세요.</p>
        </div>
      )}
    </div>
  );
}
