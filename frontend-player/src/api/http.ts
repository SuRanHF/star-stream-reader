import axios, { AxiosError } from 'axios';
import { TOKEN_KEY, useAuthStore } from '@/stores/authStore';
import type { ApiResponse } from '@/types/api';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export class ApiHttpError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiHttpError';
  }
}

export const http = axios.create({
  baseURL,
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  const token = authStore.token || localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>;
    if (payload && typeof payload === 'object' && 'success' in payload) {
      if (!payload.success) {
        throw new Error(payload.error?.message || '请求失败');
      }
      return payload.data;
    }
    return response.data;
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore();
      authStore.clearAuth();
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
    }
    const message = error.response?.data?.error?.message || error.message || '网络请求失败';
    if (error.response) {
      return Promise.reject(new ApiHttpError(
        message,
        error.response.status,
        error.response.data?.error?.code,
      ));
    }
    return Promise.reject(new Error(message));
  },
);
