'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  getRestaurant,
  getRestaurantMenus,
  createOrder,
  extractList,
  getUser,
  type Restaurant,
  type Menu,
} from '@/lib/api';

interface CartItem {
  menu: Menu;
  quantity: number;
}

function MenuCard({
  menu,
  quantity,
  onAdd,
  onRemove,
}: {
  menu: Menu;
  quantity: number;
  onAdd: () => void;
  onRemove: () => void;
}) {
  const outOfStock = menu.stock !== undefined && menu.stock <= 0;

  return (
    <div
      className={`bg-white rounded-xl border p-4 flex gap-4 transition-all ${
        outOfStock ? 'opacity-60 border-gray-100' : 'border-gray-200 hover:border-blue-200'
      }`}
    >
      {/* Icon */}
      <div className="w-16 h-16 rounded-lg bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center flex-shrink-0 text-3xl">
        🍽️
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-semibold text-gray-900 leading-snug">{menu.name}</h3>
          {outOfStock && (
            <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-500 rounded-full whitespace-nowrap">
              품절
            </span>
          )}
        </div>
        {menu.description && (
          <p className="text-sm text-gray-500 mt-0.5 line-clamp-2">{menu.description}</p>
        )}
        <div className="flex items-center justify-between mt-2">
          <div>
            <span className="font-bold text-gray-900 text-lg">
              {menu.price.toLocaleString()}원
            </span>
            {menu.stock !== undefined && menu.stock > 0 && (
              <span className="ml-2 text-xs text-gray-400">재고 {menu.stock}개</span>
            )}
          </div>

          {/* Quantity control */}
          {!outOfStock && (
            <div className="flex items-center gap-2">
              {quantity > 0 ? (
                <>
                  <button
                    onClick={onRemove}
                    className="w-8 h-8 rounded-full border border-gray-300 text-gray-600 hover:bg-gray-100 flex items-center justify-center font-bold transition-colors"
                    aria-label="수량 감소"
                  >
                    -
                  </button>
                  <span className="w-6 text-center font-semibold text-gray-900">
                    {quantity}
                  </span>
                  <button
                    onClick={onAdd}
                    className="w-8 h-8 rounded-full bg-blue-600 text-white hover:bg-blue-700 flex items-center justify-center font-bold transition-colors"
                    aria-label="수량 증가"
                  >
                    +
                  </button>
                </>
              ) : (
                <button
                  onClick={onAdd}
                  className="px-4 py-1.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 transition-colors font-medium"
                >
                  담기
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function RestaurantDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = Number(params.id);

  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [menus, setMenus] = useState<Menu[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cart, setCart] = useState<Map<number, CartItem>>(new Map());
  const [address, setAddress] = useState('서울시 강남구 테헤란로 1');
  const [ordering, setOrdering] = useState(false);
  const [orderError, setOrderError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([getRestaurant(id), getRestaurantMenus(id)])
      .then(([rRes, mRes]) => {
        if (rRes.success) setRestaurant(rRes.data);
        if (mRes.success) {
          const list = extractList(mRes.data as Parameters<typeof extractList>[0]);
          setMenus(list);
        }
      })
      .catch(() => setError('백엔드 연결 실패 - 데이터를 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, [id]);

  function addToCart(menu: Menu) {
    setCart((prev) => {
      const next = new Map(prev);
      const existing = next.get(menu.id);
      if (existing) {
        next.set(menu.id, { ...existing, quantity: existing.quantity + 1 });
      } else {
        next.set(menu.id, { menu, quantity: 1 });
      }
      return next;
    });
  }

  function removeFromCart(menuId: number) {
    setCart((prev) => {
      const next = new Map(prev);
      const existing = next.get(menuId);
      if (!existing) return prev;
      if (existing.quantity <= 1) {
        next.delete(menuId);
      } else {
        next.set(menuId, { ...existing, quantity: existing.quantity - 1 });
      }
      return next;
    });
  }

  const cartItems = Array.from(cart.values());
  const cartTotal = cartItems.reduce(
    (sum, item) => sum + item.menu.price * item.quantity,
    0
  );

  async function handleOrder() {
    const user = getUser();
    if (!user) {
      router.push('/login');
      return;
    }
    if (cartItems.length === 0) return;

    setOrdering(true);
    setOrderError(null);
    try {
      const res = await createOrder({
        restaurantId: id,
        items: cartItems.map((item) => ({
          menuId: item.menu.id,
          quantity: item.quantity,
        })),
        deliveryAddress: address,
      });
      if (res.success) {
        router.push(`/orders/${res.data.id}`);
      }
    } catch (e) {
      setOrderError(e instanceof Error ? e.message : '주문 처리 중 오류가 발생했습니다.');
    } finally {
      setOrdering(false);
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-4">
        <div className="w-10 h-10 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
        <p className="text-gray-500">음식점 정보 불러오는 중...</p>
      </div>
    );
  }

  if (error || !restaurant) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16 text-center">
        <span className="text-5xl block mb-4">🔌</span>
        <p className="text-red-600 font-semibold mb-1">연결 오류</p>
        <p className="text-red-500 text-sm">{error ?? '음식점을 찾을 수 없습니다.'}</p>
        <Link
          href="/"
          className="mt-6 inline-block px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          목록으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-gray-500 mb-6">
        <Link href="/" className="hover:text-blue-600 transition-colors">
          음식점
        </Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">{restaurant.name}</span>
      </div>

      {/* Restaurant header */}
      <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-8">
        <div className="flex items-start gap-5">
          <div className="w-20 h-20 rounded-xl bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center text-4xl flex-shrink-0">
            🍽️
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-2xl font-extrabold text-gray-900">{restaurant.name}</h1>
              {restaurant.isOpen ? (
                <span className="px-2.5 py-1 bg-green-100 text-green-700 text-xs font-semibold rounded-full">
                  영업중
                </span>
              ) : (
                <span className="px-2.5 py-1 bg-gray-100 text-gray-500 text-xs font-semibold rounded-full">
                  영업종료
                </span>
              )}
            </div>
            {restaurant.description && (
              <p className="text-gray-500 mb-3">{restaurant.description}</p>
            )}
            <div className="flex flex-wrap gap-4 text-sm text-gray-600">
              <span className="flex items-center gap-1">
                <span className="text-yellow-400">★</span>
                <span className="font-medium">{restaurant.rating?.toFixed(1) ?? '4.0'}</span>
              </span>
              {restaurant.minOrderAmount !== undefined && (
                <span>최소 주문 {restaurant.minOrderAmount.toLocaleString()}원</span>
              )}
              {restaurant.deliveryFee !== undefined && (
                <span>
                  배달비{' '}
                  {restaurant.deliveryFee === 0
                    ? '무료'
                    : `${restaurant.deliveryFee.toLocaleString()}원`}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Menu list */}
        <div className="lg:col-span-2">
          <h2 className="text-xl font-bold text-gray-900 mb-4">메뉴</h2>
          {menus.length === 0 ? (
            <div className="text-center py-16 text-gray-400 bg-white rounded-xl border border-gray-200">
              <span className="text-4xl block mb-3">🍽️</span>
              <p>등록된 메뉴가 없습니다.</p>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {menus.map((menu) => (
                <MenuCard
                  key={menu.id}
                  menu={menu}
                  quantity={cart.get(menu.id)?.quantity ?? 0}
                  onAdd={() => addToCart(menu)}
                  onRemove={() => removeFromCart(menu.id)}
                />
              ))}
            </div>
          )}
        </div>

        {/* Cart / order sidebar */}
        <div className="lg:col-span-1">
          <div className="bg-white rounded-2xl border border-gray-200 p-5 sticky top-24">
            <h2 className="text-lg font-bold text-gray-900 mb-4">주문 내역</h2>

            {cartItems.length === 0 ? (
              <div className="text-center py-8 text-gray-400">
                <span className="text-3xl block mb-2">🛒</span>
                <p className="text-sm">메뉴를 선택해주세요</p>
              </div>
            ) : (
              <>
                <div className="space-y-3 mb-4">
                  {cartItems.map((item) => (
                    <div key={item.menu.id} className="flex justify-between text-sm">
                      <span className="text-gray-700">
                        {item.menu.name}{' '}
                        <span className="text-gray-400">x{item.quantity}</span>
                      </span>
                      <span className="font-medium text-gray-900">
                        {(item.menu.price * item.quantity).toLocaleString()}원
                      </span>
                    </div>
                  ))}
                </div>
                <div className="border-t border-gray-100 pt-3 mb-4">
                  <div className="flex justify-between font-bold text-gray-900">
                    <span>합계</span>
                    <span>{cartTotal.toLocaleString()}원</span>
                  </div>
                </div>
              </>
            )}

            {/* Address */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                배달 주소
              </label>
              <input
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="배달 주소를 입력하세요"
              />
            </div>

            {orderError && (
              <p className="text-xs text-red-500 mb-3">{orderError}</p>
            )}

            <button
              onClick={handleOrder}
              disabled={cartItems.length === 0 || ordering || !restaurant.isOpen}
              className="w-full py-3 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 disabled:bg-gray-200 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
            >
              {ordering ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin"></span>
                  주문 처리 중...
                </span>
              ) : !restaurant.isOpen ? (
                '영업 종료'
              ) : (
                `${cartTotal > 0 ? cartTotal.toLocaleString() + '원 ' : ''}주문하기`
              )}
            </button>

            {!getUser() && cartItems.length > 0 && (
              <p className="text-xs text-gray-400 text-center mt-2">
                주문하려면{' '}
                <Link href="/login" className="text-blue-500 underline">
                  로그인
                </Link>
                이 필요합니다.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
