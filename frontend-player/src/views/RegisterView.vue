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
  <main class="flex min-h-screen items-center justify-center px-4 py-10">
    <form class="game-panel w-full max-w-md rounded-md p-6" @submit.prevent="submit">
      <h1 class="mb-2 text-center text-2xl text-star">创建观测账号</h1>
      <p class="mb-6 text-center text-sm text-muted">注册成为观测者，踏入星流世界。</p>
      <div class="space-y-4">
        <GameInput v-model="username" label="用户名" autocomplete="username" />
        <GameInput v-model="email" label="邮箱" type="email" autocomplete="email" />
        <GameInput v-model="password" label="密码" type="password" autocomplete="new-password" />
      </div>
      <p v-if="errorMessage" class="mt-4 rounded border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
        {{ errorMessage }}
      </p>
      <GameButton class="mt-6 w-full" type="submit" :disabled="loading">
        {{ loading ? '创建中...' : '注册并登录' }}
      </GameButton>
      <RouterLink class="mt-5 block text-center text-sm text-muted hover:text-spirit" to="/login">返回登录</RouterLink>
    </form>
  </main>
</template>
