import { createRouter, createWebHistory } from 'vue-router';
import AdminLayout from '@/layouts/AdminLayout.vue';
import AdminLoginView from '@/views/AdminLoginView.vue';
import BroadcastManageView from '@/views/BroadcastManageView.vue';
import DashboardView from '@/views/DashboardView.vue';
import FeedbackManageView from '@/views/FeedbackManageView.vue';
import LogView from '@/views/LogView.vue';
import PlayerManageView from '@/views/PlayerManageView.vue';
import SchedulerView from '@/views/SchedulerView.vue';
import TradeAuditView from '@/views/TradeAuditView.vue';
import WorldBossManageView from '@/views/WorldBossManageView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: AdminLoginView },
    {
      path: '/',
      component: AdminLayout,
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
      ],
    },
  ],
});

export default router;
