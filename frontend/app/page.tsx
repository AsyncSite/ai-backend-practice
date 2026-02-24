'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  getRestaurants,
  extractList,
  type Restaurant,
} from '@/lib/api';

const CATEGORY_LABELS: Record<string, string> = {
  전체: '전체',
  치킨: '🍗 치킨',
  피자: '🍕 피자',
  샐러드: '🥗 샐러드',
  한식: '🍚 한식',
  중식: '🥢 중식',
  양식: '🍝 양식',
};

const CATEGORY_EMOJI: Record<string, string> = {
  치킨: '🍗',
  피자: '🍕',
  샐러드: '🥗',
  한식: '🍚',
  중식: '🥢',
  양식: '🍝',
};

function getCategoryEmoji(category: string): string {
  return CATEGORY_EMOJI[category] ?? '🍽️';
}

function RestaurantCard({ restaurant }: { restaurant: Restaurant }) {
  return (
    <Link
      href={`/restaurants/${restaurant.id}`}
      className="block bg-white rounded-xl border border-gray-200 hover:border-blue-300 hover:shadow-md transition-all duration-200 overflow-hidden group"
    >
      {/* Thumbnail area */}
      <div className="h-40 bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center relative">
        <span className="text-6xl group-hover:scale-110 transition-transform duration-200">
          {getCategoryEmoji(restaurant.category)}
        </span>
        {/* Open/closed badge */}
        <div className="absolute top-3 right-3">
          {restaurant.isOpen ? (
            <span className="px-2.5 py-1 bg-green-500 text-white text-xs font-semibold rounded-full shadow-sm">
              영업중
            </span>
          ) : (
            <span className="px-2.5 py-1 bg-gray-400 text-white text-xs font-semibold rounded-full shadow-sm">
              영업종료
            </span>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="p-4">
        <div className="flex items-start justify-between gap-2 mb-1.5">
          <h3 className="font-bold text-gray-900 text-lg leading-tight group-hover:text-blue-600 transition-colors">
            {restaurant.name}
          </h3>
          <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full whitespace-nowrap">
            {restaurant.category}
          </span>
        </div>

        {restaurant.description && (
          <p className="text-sm text-gray-500 mb-3 line-clamp-2">
            {restaurant.description}
          </p>
        )}

        <div className="flex items-center justify-between text-sm text-gray-600">
          <div className="flex items-center gap-1">
            <span className="text-yellow-400">★</span>
            <span className="font-medium">
              {restaurant.rating?.toFixed(1) ?? '4.0'}
            </span>
          </div>
          <div className="flex items-center gap-3">
            {restaurant.deliveryFee !== undefined && (
              <span>
                배달비{' '}
                {restaurant.deliveryFee === 0
                  ? '무료'
                  : `${restaurant.deliveryFee.toLocaleString()}원`}
              </span>
            )}
            {restaurant.minOrderAmount !== undefined && (
              <span>최소 {restaurant.minOrderAmount.toLocaleString()}원</span>
            )}
          </div>
        </div>
      </div>
    </Link>
  );
}

export default function HomePage() {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeCategory, setActiveCategory] = useState('전체');
  const [categories, setCategories] = useState<string[]>(['전체']);

  useEffect(() => {
    setLoading(true);
    getRestaurants(0, 50)
      .then((res) => {
        if (res.success) {
          const list = extractList(res.data as Parameters<typeof extractList>[0]);
          setRestaurants(list);
          // Collect unique categories
          const cats = Array.from(new Set(list.map((r) => r.category).filter(Boolean)));
          setCategories(['전체', ...cats]);
        }
      })
      .catch(() => setError('백엔드 연결 실패 - 음식점 목록을 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, []);

  const filtered =
    activeCategory === '전체'
      ? restaurants
      : restaurants.filter((r) => r.category === activeCategory);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Hero */}
      <div className="text-center mb-10">
        <h1 className="text-3xl md:text-4xl font-extrabold text-gray-900 mb-3">
          맛있는 음식을 빠르게
        </h1>
        <p className="text-gray-500 text-lg">
          GritShop에서 원하는 음식점을 선택하세요
        </p>
      </div>

      {/* Category filter */}
      {categories.length > 1 && (
        <div className="flex flex-wrap gap-2 mb-8 justify-center">
          {categories.map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition-all duration-150 ${
                activeCategory === cat
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'bg-white text-gray-600 border border-gray-200 hover:border-blue-300 hover:text-blue-600'
              }`}
            >
              {CATEGORY_LABELS[cat] ?? cat}
            </button>
          ))}
        </div>
      )}

      {/* States */}
      {loading && (
        <div className="flex flex-col items-center justify-center py-24 gap-4">
          <div className="w-10 h-10 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
          <p className="text-gray-500">음식점 목록 불러오는 중...</p>
        </div>
      )}

      {error && !loading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-8 text-center">
          <span className="text-4xl mb-4 block">🔌</span>
          <p className="text-red-600 font-semibold mb-1">연결 오류</p>
          <p className="text-red-500 text-sm">{error}</p>
          <p className="text-gray-400 text-xs mt-3">
            백엔드 서버(http://localhost:8080)가 실행 중인지 확인하세요.
          </p>
        </div>
      )}

      {!loading && !error && filtered.length === 0 && (
        <div className="text-center py-24 text-gray-400">
          <span className="text-5xl block mb-4">🍽️</span>
          <p className="text-lg">해당 카테고리의 음식점이 없습니다.</p>
        </div>
      )}

      {/* Restaurant grid */}
      {!loading && !error && filtered.length > 0 && (
        <>
          <p className="text-sm text-gray-500 mb-4">
            총 <strong className="text-gray-800">{filtered.length}</strong>개의 음식점
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {filtered.map((r) => (
              <RestaurantCard key={r.id} restaurant={r} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
