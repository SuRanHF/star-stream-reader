<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { authApi } from '@/api/authApi';
import GameButton from '@/components/common/GameButton.vue';
import GameInput from '@/components/common/GameInput.vue';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const authStore = useAuthStore();
const username = ref('');
const email = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');

async function submit() {
  errorMessage.value = '';
  loading.value = true;
  try {
    const result = await authApi.register({
      username: username.value.trim(),
      email: email.value.trim(),
      password: password.value,
    });
    authStore.setToken(result.token, result);
    await router.push('/game');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '注册失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="relative flex min-h-screen items-center justify-center px-4 py-10 overflow-hidden">
    <!-- 背景装饰光圈 -->
    <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none">
      <div class="w-[500px] h-[500px] rounded-full bg-[radial-gradient(circle,rgba(94,196,158,0.06)_0%,transparent_60%)] absolute -translate-x-1/2 -translate-y-1/2" />
      <div class="w-[320px] h-[320px] rounded-full bg-[radial-gradient(circle,rgba(74,143,231,0.05)_0%,transparent_60%)] absolute -translate-x-1/2 -translate-y-1/2" />
    </div>

    <form
      class="game-panel relative z-10 w-full max-w-md rounded-lg p-8"
      style="animation: fadeSlideUp 0.6s var(--ease-out-expo) both;"
      @submit.prevent="submit"
    >
      <div class="mx-auto mb-8 h-[1px] w-12 bg-gradient-to-r from-transparent via-blue-400/50 to-transparent opacity-60" />

      <h1 class="mb-1 text-center text-2xl font-bold tracking-wide register-title">
        创建观测账号
      </h1>
      <p class="mb-8 text-center text-sm text-spirit/70">注册成为观测者，踏入星流世界。</p>

      <div class="space-y-5">
        <GameInput v-model="username" label="用户名" autocomplete="username" placeholder="请输入用户名" />
        <GameInput v-model="email" label="邮箱" type="email" autocomplete="email" placeholder="请输入邮箱" />
        <GameInput v-model="password" label="密码" type="password" autocomplete="new-password" placeholder="请输入密码" />
      </div>

      <p v-if="errorMessage" class="mt-4 rounded border border-danger/30 bg-danger/8 px-3 py-2.5 text-sm text-danger" style="animation: fadeSlideDown 0.2s var(--ease-out-expo) both;">
        {{ errorMessage }}
      </p>

      <GameButton class="mt-7 w-full" type="submit" :disabled="loading">
        {{ loading ? '创建中...' : '注册并登录' }}
      </GameButton>

      <RouterLink class="mt-6 block text-center text-sm text-muted hover:text-spirit transition-colors duration-200" to="/login">
        返回登录
      </RouterLink>

      <div class="mx-auto mt-8 h-[1px] w-12 bg-gradient-to-r from-transparent via-blue-400/30 to-transparent opacity-40" />
    </form>
  </main>
</template>

<style scoped>
.register-title {
  color: var(--color-system-bright, #7ab0f7);
  text-shadow: 0 0 16px rgba(74, 143, 231, 0.35);
}
</style>
