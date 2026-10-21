import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useGameStore } from '@/stores/gameStore';

describe('gameStore player progression updates', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('keeps server stage progress when an action patches the current player', () => {
    const gameStore = useGameStore();
    gameStore.player = {
      id: 14,
      playerName: '时间线读者',
      stage_progress: { storyEventsTriggered: [], bossClues: {} },
      stats: { level: 6, avatarRank: 'E' },
    };

    gameStore.patchPlayer({
      story_fragments: 3,
      stage_progress: {
        storyEventsTriggered: ['station_first_trace'],
        bossClues: { station_keeper: 1 },
      },
    });

    expect(gameStore.player?.story_fragments).toBe(3);
    expect(gameStore.player?.stage_progress).toEqual({
      storyEventsTriggered: ['station_first_trace'],
      bossClues: { station_keeper: 1 },
    });
  });
});
