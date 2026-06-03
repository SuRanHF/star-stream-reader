import axios, { AxiosError } from 'axios';
import { ADMIN_TOKEN_KEY, useAuthStore } from '@/stores/authStore';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  const token = authStore.token || localStorage.getItem(ADMIN_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['X-Admin-Key'] = 'reader-admin-2026';
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload === 'object' && 'success' in payload) {
      if (!payload.success) {
        throw new Error(payload.error?.message || '请求失败');
      }
      return payload.data;
    }
    return response.data;
  },
  (error: AxiosError<{ error?: { message?: string } }>) => {
    if (error.response?.status === 401) {
      useAuthStore().clearAuth();
      window.location.assign((import.meta.env.BASE_URL || '/') + 'login');
    }
    return Promise.reject(new Error(error.response?.data?.error?.message || error.message || '网络请求失败'));
  },
);
