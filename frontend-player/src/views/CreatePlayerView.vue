<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { playerApi } from '@/api/playerApi';
import GameButton from '@/components/common/GameButton.vue';
import GameInput from '@/components/common/GameInput.vue';

const router = useRouter();
const playerName = ref('');
const loading = ref(false);
const errorMessage = ref('');

async function submit() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await playerApi.createPlayer({ playerName: playerName.value.trim() || '未命名读者' });
    await router.push('/game');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '创建角色失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center px-4 py-10">
    <form class="game-panel w-full max-w-md rounded-md p-6" @submit.prevent="submit">
      <h1 class="mb-2 text-center text-2xl text-star">创建角色</h1>
      <p class="mb-6 text-center text-sm text-muted">角色创建先保留最小流程，不在注册时选择背后星。</p>
      <GameInput v-model="playerName" label="角色名" placeholder="留空则为未命名读者" />
      <p v-if="errorMessage" class="mt-4 rounded border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
        {{ errorMessage }}
      </p>
      <GameButton class="mt-6 w-full" type="submit" :disabled="loading">
        {{ loading ? '进入中...' : '进入游戏' }}
      </GameButton>
    </form>
  </main>
</template>
