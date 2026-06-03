<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import GameButton from '@/components/common/GameButton.vue';
import GameInput from '@/components/common/GameInput.vue';
import { authApi } from '@/api/authApi';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const authStore = useAuthStore();
const usernameOrEmail = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');

async function submit() {
  errorMessage.value = '';
  loading.value = true;
  try {
    const result = await authApi.login(usernameOrEmail.value.trim(), password.value);
    authStore.setToken(result.token, result);
    await router.push('/game');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="relative flex min-h-screen items-center justify-center px-4 py-10 overflow-hidden">
    <!-- 背景装饰光圈 -->
    <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none">
      <div class="w-[500px] h-[500px] rounded-full bg-[radial-gradient(circle,rgba(74,143,231,0.07)_0%,transparent_60%)] absolute -translate-x-1/2 -translate-y-1/2" />
      <div class="w-[320px] h-[320px] rounded-full bg-[radial-gradient(circle,rgba(94,196,158,0.05)_0%,transparent_60%)] absolute -translate-x-1/2 -translate-y-1/2" />
    </div>

    <form
      class="game-panel relative z-10 w-full max-w-md rounded-lg p-8"
      style="animation: fadeSlideUp 0.6s var(--ease-out-expo) both;"
      @submit.prevent="submit"
    >
      <!-- 顶部装饰线 -->
      <div class="mx-auto mb-8 h-[1px] w-12 bg-gradient-to-r from-transparent via-blue-400/50 to-transparent opacity-60" />

      <div class="mb-8 text-center">
        <p class="text-xs tracking-[0.5em] text-muted uppercase">Terminal of the Star Stream</p>
        <h1 class="mt-4 text-4xl font-bold tracking-[0.3em] login-title">
          灵 界
        </h1>
        <p class="mt-3 text-sm text-spirit/70">观测者登录</p>
      </div>

      <div class="space-y-5">
        <GameInput v-model="usernameOrEmail" label="用户名或邮箱" autocomplete="username" placeholder="请输入用户名或邮箱" />
        <GameInput v-model="password" label="密码" type="password" autocomplete="current-password" placeholder="请输入密码" />
      </div>

      <p v-if="errorMessage" class="mt-4 rounded border border-danger/30 bg-danger/8 px-3 py-2.5 text-sm text-danger" style="animation: fadeSlideDown 0.2s var(--ease-out-expo) both;">
        {{ errorMessage }}
      </p>

      <GameButton class="mt-7 w-full" type="submit" :disabled="loading">
        {{ loading ? '连接星流中...' : '进入星流' }}
      </GameButton>

      <div class="mt-6 flex items-center justify-between text-sm">
        <RouterLink class="text-muted hover:text-spirit transition-colors duration-200" to="/register">创建观测账号</RouterLink>
        <span class="text-muted/60 text-xs tracking-wider">星流 · 贰</span>
      </div>

      <!-- 底部装饰线 -->
      <div class="mx-auto mt-8 h-[1px] w-12 bg-gradient-to-r from-transparent via-blue-400/30 to-transparent opacity-40" />
    </form>
  </main>
</template>

<style scoped>
.login-title {
  color: var(--color-system-bright, #7ab0f7);
  text-shadow: 0 0 24px rgba(74, 143, 231, 0.4);
}
</style>
