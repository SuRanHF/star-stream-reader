import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';
import CreatePlayerView from '@/views/CreatePlayerView.vue';
import GameView from '@/views/GameView.vue';
import LoginView from '@/views/LoginView.vue';
import RegisterView from '@/views/RegisterView.vue';
import SceneMapDemo from '@/views/SceneMapDemo.vue';
import IntegrationDemo from '@/views/IntegrationDemo.vue';
import GameSceneMapPreview from '@/views/GameSceneMapPreview.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/game' },
    { path: '/login', component: LoginView },
    { path: '/register', component: RegisterView },
    { path: '/create-player', component: CreatePlayerView },
    { path: '/game', component: GameView, meta: { requiresAuth: true } },
    { path: '/scene-map-demo', component: SceneMapDemo },
    { path: '/integration-demo', component: IntegrationDemo },
    { path: '/scene-map-preview', component: GameSceneMapPreview },
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
