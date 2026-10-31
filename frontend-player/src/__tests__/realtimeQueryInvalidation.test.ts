import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { queryClient } from '@/query/queryClient';
import { realtimeClient } from '@/realtime/realtimeClient';
import { useGameStore } from '@/stores/gameStore';

describe('realtime query invalidation', () => {
  let invalidateQueries: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    setActivePinia(createPinia());
    useGameStore().player = { id: 42 };
    invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
    vi.spyOn(console, 'debug').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('refreshes this player’s quest panel after a completion event', () => {
    realtimeClient.handleMessage(JSON.stringify({ type: 'quest.completed', data: { questKey: 'daily_1' } }));

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['feature-panel', 'quests', 42],
    });
  });

  it('refreshes this player’s world boss panel after a boss update', () => {
    realtimeClient.handleMessage(JSON.stringify({ type: 'worldBoss.hp.updated', data: { hp: 900 } }));

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['feature-panel', 'worldBoss', 42],
    });
  });
});
