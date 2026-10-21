import { beforeEach, describe, expect, it } from 'vitest';
import type { ExploreResult } from '@/api/exploreApi';
import {
  clearPendingCombatRequestKey,
  getPendingCombatRequestKey,
} from '@/utils/pendingCombatRequestKey';

function makeBattle(overrides: Partial<ExploreResult> = {}): ExploreResult {
  return {
    result_type: 'battle',
    monster_key: 'station_rat',
    result: { event_key: 'station_rat_encounter', event_type: 'battle', name: '变异站鼠', description: '' },
    rewards: {},
    progress_effects: {},
    new_titles: [],
    stories_exhausted: false,
    ...overrides,
  };
}

describe('pendingCombatRequestKey', () => {
  beforeEach(() => localStorage.clear());

  it('使用服务端探索回执键作为可跨设备恢复的战斗幂等键', () => {
    const result = makeBattle({ request_key: 'explore-receipt-42' });

    expect(getPendingCombatRequestKey(7, result)).toBe('explore-receipt-42');
    expect(getPendingCombatRequestKey(7, result)).toBe('explore-receipt-42');
    expect(localStorage.length).toBe(0);
  });

  it('旧遭遇没有服务端请求键时将备用键保存在本地直到结算', () => {
    const result = makeBattle();
    const requestKey = getPendingCombatRequestKey(7, result);

    expect(getPendingCombatRequestKey(7, result)).toBe(requestKey);
    expect(localStorage.length).toBe(1);

    clearPendingCombatRequestKey(7, result, requestKey);

    expect(localStorage.length).toBe(0);
    expect(getPendingCombatRequestKey(7, result)).not.toBe(requestKey);
  });
});
