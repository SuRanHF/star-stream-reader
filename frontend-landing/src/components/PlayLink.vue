<script setup lang="ts">
import { PLAY_URL } from '@/store';

// 进入游戏的按钮。游戏还没上线时（构建时 VITE_PLAY_URL=soon）不跳转，显示「即将开放」
defineProps<{ text: string }>();
</script>

<template>
  <a v-if="PLAY_URL" class="cta" :href="PLAY_URL">{{ text }} <i>→</i></a>
  <span v-else class="cta cta-soon" aria-disabled="true">即将开放</span>
</template>
