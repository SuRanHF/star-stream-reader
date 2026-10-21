import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useGameStore } from '@/stores/gameStore';

describe('gameStore transient action logs', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('keeps repeated identical results from distinct actions as separate history entries', () => {
    const game = useGameStore();
    const result = [{ message: '你选择了「继续前进」', type: 'story' }];

    game.replaceTransientActionLogs('choice-request-1', result);
    game.replaceTransientActionLogs('choice-request-2', result);

    expect(game.recentLogs.map((entry) => entry.message)).toEqual([
      '你选择了「继续前进」',
      '你选择了「继续前进」',
    ]);
  });

  it('replaces only the transient rows belonging to a retried idempotent action', () => {
    const game = useGameStore();
    game.replaceTransientActionLogs('choice-request-1', [
      { message: '你选择了「继续前进」', type: 'story' },
      { message: '获得星币 +5', type: 'reward' },
    ]);
    game.replaceTransientActionLogs('choice-request-2', [
      { message: '你选择了「继续前进」', type: 'story' },
      { message: '获得星币 +9', type: 'reward' },
    ]);
    game.replaceTransientActionLogs('choice-request-1', [
      { message: '你选择了「继续前进」', type: 'story' },
      { message: '获得星币 +5', type: 'reward' },
    ]);

    expect(game.recentLogs.map((entry) => entry.message)).toEqual([
      '你选择了「继续前进」',
      '获得星币 +5',
      '你选择了「继续前进」',
      '获得星币 +9',
    ]);
  });
});
