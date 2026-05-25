<script setup lang="ts">
import { watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { useRouter } from 'vue-router';
import { gameApi } from '@/api/gameApi';
import GameButton from '@/components/common/GameButton.vue';
import CreatePlayerView from '@/views/CreatePlayerView.vue';
import GameLayout from '@/layouts/GameLayout.vue';
import { realtimeClient } from '@/realtime/realtimeClient';
import { useAuthStore } from '@/stores/authStore';
import { useGameStore } from '@/stores/gameStore';

const router = useRouter();
const authStore = useAuthStore();
const gameStore = useGameStore();

if (!authStore.token) {
  authStore.loadFromStorage();
}

if (!authStore.token) {
  router.replace('/login');
}

const bootstrapQuery = useQuery({
  queryKey: ['game-bootstrap'],
  queryFn: gameApi.getBootstrap,
  retry: 1,
});

watch(
  () => bootstrapQuery.data.value,
  (payload) => {
    if (!payload) return;
    gameStore.applyBootstrap(payload);
    if (authStore.token && payload.player) {
      realtimeClient.connect(authStore.token);
    }
  },
  { immediate: true },
);
</script>

<template>
  <main v-if="bootstrapQuery.isLoading.value" class="flex min-h-screen items-center justify-center text-star">
    正在读取世界线...
  </main>

  <main v-else-if="bootstrapQuery.error.value" class="flex min-h-screen items-center justify-center px-4">
    <section class="game-panel max-w-md rounded-md p-6 text-center">
      <h1 class="text-xl text-danger">启动失败</h1>
      <p class="mt-3 text-sm text-muted">{{ bootstrapQuery.error.value.message }}</p>
      <GameButton class="mt-5" @click="bootstrapQuery.refetch()">重试</GameButton>
    </section>
  </main>

  <CreatePlayerView v-else-if="!gameStore.player" />

  <GameLayout v-else />
</template>
