// API response types
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface PagedData<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

export interface Restaurant {
  id: number;
  name: string;
  category: string;
  description: string;
  rating: number;
  isOpen: boolean;
  minOrderAmount: number;
  deliveryFee: number;
  imageUrl?: string;
}

export interface Menu {
  id: number;
  restaurantId: number;
  name: string;
  description: string;
  price: number;
  stock: number;
  category: string;
  imageUrl?: string;
}

export interface Order {
  id: number;
  userId: number;
  restaurantId: number;
  restaurantName: string;
  status: OrderStatus;
  totalAmount: number;
  deliveryAddress: string;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
}

export interface OrderItem {
  menuId: number;
  menuName: string;
  quantity: number;
  price: number;
}

export interface Payment {
  id: number;
  orderId: number;
  amount: number;
  status: string;
  paymentMethod: string;
  paidAt?: string;
}

export interface ServerInfo {
  serverId: string;
  hostname: string;
  version: string;
  startTime: string;
}

export interface User {
  id: number;
  email: string;
  name: string;
  phone?: string;
}

export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'PREPARING'
  | 'DELIVERING'
  | 'COMPLETED'
  | 'CANCELLED';

// Token management (client-side only)
export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('gritshop_token');
}

export function setToken(token: string): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem('gritshop_token', token);
}

export function removeToken(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('gritshop_token');
}

export function getUser(): User | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem('gritshop_user');
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function setUser(user: User): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem('gritshop_user', JSON.stringify(user));
}

export function removeUser(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('gritshop_user');
}

// Core fetch wrapper
async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const token = getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`/api${path}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(
      errorText || `HTTP ${res.status}: ${res.statusText}`
    );
  }

  const json = (await res.json()) as ApiResponse<T>;
  return json;
}

// Restaurant APIs
export async function getRestaurants(
  page = 0,
  size = 20
): Promise<ApiResponse<PagedData<Restaurant> | Restaurant[]>> {
  return apiFetch(`/restaurants?page=${page}&size=${size}`);
}

export async function getRestaurant(
  id: number
): Promise<ApiResponse<Restaurant>> {
  return apiFetch(`/restaurants/${id}`);
}

export async function getRestaurantMenus(
  id: number
): Promise<ApiResponse<Menu[] | PagedData<Menu>>> {
  return apiFetch(`/restaurants/${id}/menus`);
}

// Auth APIs
export async function login(
  email: string,
  password: string
): Promise<ApiResponse<{ token: string; user: User }>> {
  return apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export async function signup(body: {
  email: string;
  password: string;
  name: string;
  phone?: string;
}): Promise<ApiResponse<User>> {
  return apiFetch('/users/signup', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

// Order APIs
export async function getOrders(
  page = 0,
  size = 20
): Promise<ApiResponse<PagedData<Order> | Order[]>> {
  return apiFetch(`/orders?page=${page}&size=${size}`);
}

export async function getOrder(id: number): Promise<ApiResponse<Order>> {
  return apiFetch(`/orders/${id}`);
}

export async function createOrder(body: {
  restaurantId: number;
  items: { menuId: number; quantity: number }[];
  deliveryAddress: string;
}): Promise<ApiResponse<Order>> {
  return apiFetch('/orders', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

// Payment API
export async function getPayment(
  orderId: number
): Promise<ApiResponse<Payment>> {
  return apiFetch(`/payments/${orderId}`);
}

// Stock API
export async function decreaseStock(
  menuId: number,
  useLock: boolean
): Promise<ApiResponse<{ stock: number }>> {
  return apiFetch(`/menus/${menuId}/decrease-stock?lock=${useLock}`, {
    method: 'POST',
  });
}

// Server info API
export async function getServerInfo(): Promise<ApiResponse<ServerInfo>> {
  return apiFetch('/server-info');
}

// Helper to extract list from paged or direct array response
export function extractList<T>(
  data: PagedData<T> | T[]
): T[] {
  if (Array.isArray(data)) return data;
  return data.content ?? [];
}
