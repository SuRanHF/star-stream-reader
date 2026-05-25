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
  <main class="flex min-h-screen items-center justify-center px-4 py-10">
    <form class="game-panel w-full max-w-md rounded-md p-6" @submit.prevent="submit">
      <div class="mb-8 text-center">
        <p class="text-xs tracking-[0.4em] text-muted">星流终端</p>
        <h1 class="mt-3 text-3xl text-star">灵界</h1>
        <p class="mt-2 text-sm text-muted">观测者登录</p>
      </div>

      <div class="space-y-4">
        <GameInput v-model="usernameOrEmail" label="用户名或邮箱" autocomplete="username" placeholder="请输入用户名或邮箱" />
        <GameInput v-model="password" label="密码" type="password" autocomplete="current-password" placeholder="请输入密码" />
      </div>

      <p v-if="errorMessage" class="mt-4 rounded border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
        {{ errorMessage }}
      </p>

      <GameButton class="mt-6 w-full" type="submit" :disabled="loading">
        {{ loading ? '连接星流中...' : '登录' }}
      </GameButton>

      <div class="mt-5 flex items-center justify-between text-sm">
        <RouterLink class="text-muted hover:text-spirit" to="/register">创建账号</RouterLink>
        <span class="text-muted">星流·贰</span>
      </div>
    </form>
  </main>
</template>
