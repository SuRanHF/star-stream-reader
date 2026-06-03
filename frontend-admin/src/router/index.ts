import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';
import AdminLayout from '@/layouts/AdminLayout.vue';
import AdminLoginView from '@/views/AdminLoginView.vue';
import BroadcastManageView from '@/views/BroadcastManageView.vue';
import DashboardView from '@/views/DashboardView.vue';
import FeedbackManageView from '@/views/FeedbackManageView.vue';
import LogView from '@/views/LogView.vue';
import PlayerManageView from '@/views/PlayerManageView.vue';
import SchedulerView from '@/views/SchedulerView.vue';
import AdminCrudView from '@/views/AdminCrudView.vue';
import TradeAuditView from '@/views/TradeAuditView.vue';
import WorldBossManageView from '@/views/WorldBossManageView.vue';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: AdminLoginView },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: DashboardView },
        { path: 'players', component: PlayerManageView },
        { path: 'broadcast', component: BroadcastManageView },
        { path: 'world-boss', component: WorldBossManageView },
        { path: 'scheduler', component: SchedulerView },
        { path: 'trade-audit', component: TradeAuditView },
        { path: 'logs', component: LogView },
        { path: 'feedback', component: FeedbackManageView },
        { path: 'crud', component: AdminCrudView },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return '/login';
  }
  if (to.path === '/login' && auth.isAuthenticated) {
    return '/dashboard';
  }
  return true;
});

export default router;
