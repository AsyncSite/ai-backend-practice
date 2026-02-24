'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getUser, removeToken, removeUser, type User } from '@/lib/api';

export default function Header() {
  const [user, setUser] = useState<User | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    setUser(getUser());
    const onStorage = () => setUser(getUser());
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  function handleLogout() {
    removeToken();
    removeUser();
    setUser(null);
    window.location.href = '/';
  }

  return (
    <header className="bg-white border-b border-gray-200 sticky top-0 z-50 shadow-sm">
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link
          href="/"
          className="flex items-center gap-2 font-bold text-xl text-blue-600 hover:text-blue-700 transition-colors"
        >
          <span className="text-2xl">🛒</span>
          <span>GritShop</span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden md:flex items-center gap-6">
          <Link
            href="/"
            className="text-gray-600 hover:text-blue-600 font-medium transition-colors"
          >
            음식점
          </Link>
          <Link
            href="/orders"
            className="text-gray-600 hover:text-blue-600 font-medium transition-colors"
          >
            주문내역
          </Link>
          <Link
            href="/stock-demo"
            className="text-gray-600 hover:text-blue-600 font-medium transition-colors"
          >
            동시성 데모
          </Link>
        </nav>

        {/* Right side */}
        <div className="flex items-center gap-3">
          {/* Notification bell */}
          <button
            className="relative p-2 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-full transition-colors"
            aria-label="알림"
          >
            <svg
              className="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
              />
            </svg>
            <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full"></span>
          </button>

          {user ? (
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-700 hidden md:block">
                <span className="font-medium">{user.name}</span>님
              </span>
              <button
                onClick={handleLogout}
                className="text-sm px-4 py-2 border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                href="/login"
                className="text-sm px-4 py-2 text-gray-600 hover:text-blue-600 font-medium transition-colors"
              >
                로그인
              </Link>
              <Link
                href="/register"
                className="text-sm px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
              >
                회원가입
              </Link>
            </div>
          )}

          {/* Mobile hamburger */}
          <button
            className="md:hidden p-2 text-gray-500 hover:text-blue-600 rounded-lg"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label="메뉴 열기"
          >
            <svg
              className="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              {menuOpen ? (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              ) : (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 6h16M4 12h16M4 18h16"
                />
              )}
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="md:hidden border-t border-gray-100 bg-white px-4 py-3 flex flex-col gap-3">
          <Link
            href="/"
            className="text-gray-700 hover:text-blue-600 font-medium py-2"
            onClick={() => setMenuOpen(false)}
          >
            음식점
          </Link>
          <Link
            href="/orders"
            className="text-gray-700 hover:text-blue-600 font-medium py-2"
            onClick={() => setMenuOpen(false)}
          >
            주문내역
          </Link>
          <Link
            href="/stock-demo"
            className="text-gray-700 hover:text-blue-600 font-medium py-2"
            onClick={() => setMenuOpen(false)}
          >
            동시성 데모
          </Link>
        </div>
      )}
    </header>
  );
}
