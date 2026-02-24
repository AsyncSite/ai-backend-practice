'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { getOrders, extractList, getUser, type Order, type OrderStatus } from '@/lib/api';

const STATUS_CONFIG: Record<
  OrderStatus,
  { label: string; color: string; bg: string }
> = {
  PENDING: {
    label: '결제 대기',
    color: 'text-gray-600',
    bg: 'bg-gray-100',
  },
  PAID: {
    label: '결제 완료',
    color: 'text-blue-600',
    bg: 'bg-blue-100',
  },
  PREPARING: {
    label: '준비 중',
    color: 'text-yellow-700',
    bg: 'bg-yellow-100',
  },
  DELIVERING: {
    label: '배달 중',
    color: 'text-purple-700',
    bg: 'bg-purple-100',
  },
  COMPLETED: {
    label: '배달 완료',
    color: 'text-green-700',
    bg: 'bg-green-100',
  },
  CANCELLED: {
    label: '취소됨',
    color: 'text-red-600',
    bg: 'bg-red-100',
  },
};

function StatusBadge({ status }: { status: OrderStatus }) {
  const cfg = STATUS_CONFIG[status] ?? {
    label: status,
    color: 'text-gray-600',
    bg: 'bg-gray-100',
  };
  return (
    <span
      className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${cfg.bg} ${cfg.color}`}
    >
      {cfg.label}
    </span>
  );
}

function OrderCard({ order }: { order: Order }) {
  const date = new Date(order.createdAt);
  const formattedDate = isNaN(date.getTime())
    ? order.createdAt
    : date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });

  const itemSummary = order.items
    ?.slice(0, 2)
    .map((i) => `${i.menuName} x${i.quantity}`)
    .join(', ');
  const moreCount = (order.items?.length ?? 0) - 2;

  return (
    <Link
      href={`/orders/${order.id}`}
      className="block bg-white rounded-xl border border-gray-200 hover:border-blue-300 hover:shadow-md transition-all duration-200 p-5"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <h3 className="font-bold text-gray-900 text-lg leading-tight">
            {order.restaurantName ?? `음식점 #${order.restaurantId}`}
          </h3>
          <p className="text-xs text-gray-400 mt-0.5">{formattedDate}</p>
        </div>
        <StatusBadge status={order.status} />
      </div>

      {itemSummary && (
        <p className="text-sm text-gray-500 mb-3">
          {itemSummary}
          {moreCount > 0 && (
            <span className="text-gray-400"> 외 {moreCount}개</span>
          )}
        </p>
      )}

      <div className="flex items-center justify-between">
        <span className="font-bold text-gray-900">
          {order.totalAmount?.toLocaleString()}원
        </span>
        <span className="text-sm text-blue-600 hover:underline">
          상세보기 →
        </span>
      </div>
    </Link>
  );
}

export default function OrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const user = getUser();
    if (!user) {
      router.push('/login');
      return;
    }

    setLoading(true);
    getOrders(0, 50)
      .then((res) => {
        if (res.success) {
          const list = extractList(res.data as Parameters<typeof extractList>[0]);
          // Sort newest first
          const sorted = [...list].sort(
            (a, b) =>
              new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          );
          setOrders(sorted);
        }
      })
      .catch(() => setError('백엔드 연결 실패 - 주문 목록을 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, [router]);

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-extrabold text-gray-900">주문 내역</h1>
        <Link
          href="/"
          className="text-sm text-blue-600 hover:underline"
        >
          음식점 둘러보기
        </Link>
      </div>

      {loading && (
        <div className="flex flex-col items-center justify-center py-24 gap-4">
          <div className="w-10 h-10 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
          <p className="text-gray-500">주문 내역 불러오는 중...</p>
        </div>
      )}

      {error && !loading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-8 text-center">
          <span className="text-4xl mb-4 block">🔌</span>
          <p className="text-red-600 font-semibold mb-1">연결 오류</p>
          <p className="text-red-500 text-sm">{error}</p>
        </div>
      )}

      {!loading && !error && orders.length === 0 && (
        <div className="text-center py-24 bg-white rounded-2xl border border-gray-200">
          <span className="text-5xl block mb-4">📦</span>
          <p className="text-lg font-semibold text-gray-700 mb-2">주문 내역이 없습니다</p>
          <p className="text-gray-400 text-sm mb-6">첫 번째 주문을 해보세요!</p>
          <Link
            href="/"
            className="inline-block px-6 py-2.5 bg-blue-600 text-white rounded-xl font-medium hover:bg-blue-700 transition-colors"
          >
            음식점 보기
          </Link>
        </div>
      )}

      {!loading && !error && orders.length > 0 && (
        <div className="flex flex-col gap-3">
          {orders.map((order) => (
            <OrderCard key={order.id} order={order} />
          ))}
        </div>
      )}
    </div>
  );
}
