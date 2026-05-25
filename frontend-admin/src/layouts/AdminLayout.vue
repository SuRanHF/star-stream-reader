<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DataAnalysis, Document, Monitor, Operation, SwitchButton, User, Wallet } from '@element-plus/icons-vue';
import { useAuthStore } from '@/stores/authStore';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const activeMenu = computed(() => route.path);

const menuItems = [
  { path: '/dashboard', label: 'Dashboard', icon: DataAnalysis },
  { path: '/players', label: '玩家管理', icon: User },
  { path: '/broadcast', label: '星流放送', icon: Monitor },
  { path: '/world-boss', label: '世界 Boss', icon: Operation },
  { path: '/scheduler', label: '调度器', icon: DataAnalysis },
  { path: '/trade-audit', label: '交易审计', icon: Wallet },
  { path: '/feedback', label: '反馈管理', icon: Document },
  { path: '/logs', label: '日志', icon: Document },
];

async function logout() {
  authStore.clearAuth();
  await router.push('/login');
}
</script>

<template>
  <el-container class="min-h-screen">
    <el-aside width="220px">
      <div class="border-b px-5 py-4">
        <h1 class="text-lg font-semibold">灵界后台</h1>
        <p class="mt-1 text-xs text-gray-500">Phase 9B</p>
      </div>
      <el-menu :default-active="activeMenu" router>
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="flex items-center justify-between border-b bg-white">
        <span class="text-sm text-gray-500">Spring Boot 管理端骨架</span>
        <div class="flex items-center gap-3">
          <span class="text-sm">{{ authStore.adminName }}</span>
          <el-button :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
