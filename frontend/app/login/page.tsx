'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { login, setToken, setUser } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('customer1@test.com');
  const [password, setPassword] = useState('password123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await login(email, password);
      if (res.success && res.data) {
        if (res.data.token) {
          setToken(res.data.token);
        }
        if (res.data.user) {
          setUser(res.data.user);
        }
        // Dispatch storage event so Header updates
        window.dispatchEvent(new Event('storage'));
        router.push('/');
      } else {
        setError('로그인에 실패했습니다.');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : '';
      if (msg.includes('401') || msg.toLowerCase().includes('unauthorized')) {
        setError('이메일 또는 비밀번호가 올바르지 않습니다.');
      } else if (msg.includes('Failed to fetch') || msg.includes('ECONNREFUSED')) {
        setError('백엔드 연결 실패 - 서버가 실행 중인지 확인하세요.');
      } else {
        setError(msg || '로그인 중 오류가 발생했습니다.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-[calc(100vh-8rem)] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
          {/* Logo */}
          <div className="text-center mb-8">
            <span className="text-4xl block mb-3">🛒</span>
            <h1 className="text-2xl font-extrabold text-gray-900">로그인</h1>
            <p className="text-gray-500 text-sm mt-1">GritShop에 오신 것을 환영합니다</p>
          </div>

          {/* Test credentials notice */}
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6">
            <p className="text-xs font-semibold text-blue-700 mb-2">테스트 계정 (자동 입력됨)</p>
            <div className="space-y-1 text-xs text-blue-600 font-mono">
              <div>이메일: customer1@test.com</div>
              <div>비밀번호: password123</div>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                이메일
              </label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-shadow"
                placeholder="이메일을 입력하세요"
                autoComplete="email"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                비밀번호
              </label>
              <input
                id="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-shadow"
                placeholder="비밀번호를 입력하세요"
                autoComplete="current-password"
              />
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3">
                <p className="text-sm text-red-600">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 disabled:bg-blue-300 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin"></span>
                  로그인 중...
                </>
              ) : (
                '로그인'
              )}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            계정이 없으신가요?{' '}
            <Link
              href="/register"
              className="text-blue-600 font-medium hover:underline"
            >
              회원가입
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
