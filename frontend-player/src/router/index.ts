import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/game' },
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', component: () => import('@/views/RegisterView.vue') },
    { path: '/create-player', component: () => import('@/views/CreatePlayerView.vue') },
    { path: '/game', component: () => import('@/views/GameView.vue'), meta: { requiresAuth: true } },
  ],
});

router.beforeEach((to) => {
  const authStore = useAuthStore();
  authStore.loadFromStorage();
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return '/login';
  }
  // 已登录用户允许访问登录/注册页（切换账号等场景）
  return true;
});

export default router;
