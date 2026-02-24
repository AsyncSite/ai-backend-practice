'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getOrder, getPayment, type Order, type Payment, type OrderStatus } from '@/lib/api';

const STATUS_STEPS: OrderStatus[] = [
  'PENDING',
  'PAID',
  'PREPARING',
  'DELIVERING',
  'COMPLETED',
];

const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  PREPARING: '준비 중',
  DELIVERING: '배달 중',
  COMPLETED: '배달 완료',
  CANCELLED: '취소됨',
};

const STATUS_ICONS: Record<string, string> = {
  PENDING: '🕐',
  PAID: '💳',
  PREPARING: '👨‍🍳',
  DELIVERING: '🛵',
  COMPLETED: '✅',
  CANCELLED: '❌',
};

const STATUS_BADGE: Record<
  OrderStatus,
  { bg: string; text: string }
> = {
  PENDING: { bg: 'bg-gray-100', text: 'text-gray-600' },
  PAID: { bg: 'bg-blue-100', text: 'text-blue-700' },
  PREPARING: { bg: 'bg-yellow-100', text: 'text-yellow-700' },
  DELIVERING: { bg: 'bg-purple-100', text: 'text-purple-700' },
  COMPLETED: { bg: 'bg-green-100', text: 'text-green-700' },
  CANCELLED: { bg: 'bg-red-100', text: 'text-red-600' },
};

function StatusTracker({ status }: { status: OrderStatus }) {
  if (status === 'CANCELLED') {
    return (
      <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">
        <span className="text-3xl block mb-2">❌</span>
        <p className="text-red-600 font-semibold">주문이 취소되었습니다</p>
      </div>
    );
  }

  const currentIndex = STATUS_STEPS.indexOf(status);

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <h3 className="font-bold text-gray-900 mb-6">주문 진행 상태</h3>
      <div className="relative">
        {/* Connecting line */}
        <div className="absolute top-5 left-5 right-5 h-0.5 bg-gray-200">
          <div
            className="h-full bg-blue-500 transition-all duration-700"
            style={{
              width:
                currentIndex <= 0
                  ? '0%'
                  : `${(currentIndex / (STATUS_STEPS.length - 1)) * 100}%`,
            }}
          />
        </div>

        {/* Steps */}
        <div className="relative flex justify-between">
          {STATUS_STEPS.map((step, idx) => {
            const isDone = idx < currentIndex;
            const isCurrent = idx === currentIndex;
            const isPending = idx > currentIndex;

            return (
              <div key={step} className="flex flex-col items-center gap-2 w-16">
                {/* Circle */}
                <div
                  className={`w-10 h-10 rounded-full flex items-center justify-center text-lg transition-all duration-300 z-10 ${
                    isDone
                      ? 'bg-blue-500 text-white shadow-md'
                      : isCurrent
                      ? 'bg-blue-600 text-white shadow-lg ring-4 ring-blue-100'
                      : 'bg-white border-2 border-gray-200 text-gray-300'
                  }`}
                >
                  {isDone ? (
                    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                      <path
                        fillRule="evenodd"
                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                        clipRule="evenodd"
                      />
                    </svg>
                  ) : (
                    <span className={isPending ? 'opacity-30' : ''}>
                      {STATUS_ICONS[step]}
                    </span>
                  )}
                </div>

                {/* Label */}
                <span
                  className={`text-xs text-center leading-tight font-medium ${
                    isCurrent
                      ? 'text-blue-600'
                      : isDone
                      ? 'text-gray-600'
                      : 'text-gray-300'
                  }`}
                >
                  {STATUS_LABELS[step]}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default function OrderDetailPage() {
  const params = useParams();
  const id = Number(params.id);

  const [order, setOrder] = useState<Order | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getOrder(id)
      .then((res) => {
        if (res.success) {
          setOrder(res.data);
          // Fetch payment info in parallel
          return getPayment(id);
        }
      })
      .then((pRes) => {
        if (pRes?.success) setPayment(pRes.data);
      })
      .catch(() => setError('백엔드 연결 실패 - 주문 정보를 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-4">
        <div className="w-10 h-10 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
        <p className="text-gray-500">주문 정보 불러오는 중...</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16 text-center">
        <span className="text-5xl block mb-4">🔌</span>
        <p className="text-red-600 font-semibold mb-1">연결 오류</p>
        <p className="text-red-500 text-sm">{error ?? '주문을 찾을 수 없습니다.'}</p>
        <Link
          href="/orders"
          className="mt-6 inline-block px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          주문 목록으로
        </Link>
      </div>
    );
  }

  const badge = STATUS_BADGE[order.status] ?? STATUS_BADGE.PENDING;
  const formattedDate = order.createdAt
    ? new Date(order.createdAt).toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '-';

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-gray-500 mb-6">
        <Link href="/orders" className="hover:text-blue-600 transition-colors">
          주문 내역
        </Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">주문 #{order.id}</span>
      </div>

      {/* Header */}
      <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-5">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h1 className="text-xl font-extrabold text-gray-900">
              {order.restaurantName ?? `음식점 #${order.restaurantId}`}
            </h1>
            <p className="text-sm text-gray-400 mt-0.5">주문 번호: #{order.id}</p>
            <p className="text-xs text-gray-400 mt-0.5">{formattedDate}</p>
          </div>
          <span
            className={`inline-flex items-center px-3 py-1.5 rounded-full text-sm font-semibold ${badge.bg} ${badge.text}`}
          >
            {STATUS_ICONS[order.status]} {STATUS_LABELS[order.status]}
          </span>
        </div>

        {order.deliveryAddress && (
          <div className="flex items-center gap-2 text-sm text-gray-600 bg-gray-50 rounded-lg px-4 py-2.5">
            <span>📍</span>
            <span>{order.deliveryAddress}</span>
          </div>
        )}
      </div>

      {/* Status tracker */}
      <div className="mb-5">
        <StatusTracker status={order.status} />
      </div>

      {/* Order items */}
      <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-5">
        <h3 className="font-bold text-gray-900 mb-4">주문 항목</h3>
        {order.items && order.items.length > 0 ? (
          <div className="space-y-3">
            {order.items.map((item, idx) => (
              <div
                key={idx}
                className="flex items-center justify-between py-2 border-b border-gray-50 last:border-0"
              >
                <div className="flex items-center gap-3">
                  <span className="w-8 h-8 bg-gray-100 rounded-lg flex items-center justify-center text-sm font-semibold text-gray-600">
                    {item.quantity}
                  </span>
                  <span className="text-gray-800 font-medium">{item.menuName}</span>
                </div>
                <span className="font-semibold text-gray-900">
                  {(item.price * item.quantity).toLocaleString()}원
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-gray-400 text-sm">주문 항목 정보가 없습니다.</p>
        )}

        {/* Total */}
        <div className="mt-4 pt-4 border-t border-gray-200 flex justify-between">
          <span className="font-bold text-gray-900 text-lg">합계</span>
          <span className="font-bold text-blue-600 text-lg">
            {order.totalAmount?.toLocaleString()}원
          </span>
        </div>
      </div>

      {/* Payment info */}
      {payment && (
        <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-5">
          <h3 className="font-bold text-gray-900 mb-4">결제 정보</h3>
          <div className="space-y-3 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-500">결제 상태</span>
              <span className="font-medium text-gray-900">{payment.status}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">결제 방법</span>
              <span className="font-medium text-gray-900">{payment.paymentMethod}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">결제 금액</span>
              <span className="font-bold text-gray-900">
                {payment.amount?.toLocaleString()}원
              </span>
            </div>
            {payment.paidAt && (
              <div className="flex justify-between">
                <span className="text-gray-500">결제 시각</span>
                <span className="font-medium text-gray-900">
                  {new Date(payment.paidAt).toLocaleDateString('ko-KR', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Back button */}
      <Link
        href="/orders"
        className="block w-full text-center py-3 border border-gray-300 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors font-medium"
      >
        주문 목록으로 돌아가기
      </Link>
    </div>
  );
}
