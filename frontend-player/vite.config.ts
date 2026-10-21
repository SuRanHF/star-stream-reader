/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 3173,
    // 允许樱花内网穿透(Sakura Frp)的域名访问，否则 Vite 会拦截 DNS 重绑定
    allowedHosts: ['.nyat.app', '.natfrp.cloud', '.sakurafrp.com'],
    proxy: {
      '/api': {
        target: 'http://localhost:3174',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:3174',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'jsdom',
    globals: true,
    exclude: ['**/node_modules/**', 'e2e/**', 'test-results/**'],
  },
});
