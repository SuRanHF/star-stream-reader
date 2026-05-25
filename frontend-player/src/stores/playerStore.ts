import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { Player } from '@/types/player';

export const usePlayerStore = defineStore('player', () => {
  const current = ref<Player | null>(null);

  function setPlayer(player: Player | null) {
    current.value = player;
  }

  return { current, setPlayer };
});
