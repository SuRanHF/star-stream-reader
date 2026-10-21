import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import { http } from '@/api/http';
import { equipmentApi } from '@/api/equipmentApi';
import { inventoryApi } from '@/api/inventoryApi';
import { pkApi } from '@/api/pkApi';
import { playerApi } from '@/api/playerApi';
import { questApi } from '@/api/questApi';
import { skillApi } from '@/api/skillApi';
import { worldBossApi } from '@/api/worldBossApi';

const postMock = vi.mocked(http.post);

describe('inventoryApi request keys', () => {
  beforeEach(() => {
    localStorage.clear();
    postMock.mockReset();
    postMock.mockResolvedValue({ data: { success: true, data: {} } } as never);
  });

  it('single use and sell requests include a stable key', async () => {
    await inventoryApi.useItem(7, 'stamina_bread');
    await inventoryApi.sellItem(7, 'old_coin', 3);

    expect(postMock.mock.calls[0]?.[0]).toBe('/inventory/use');
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({
      playerId: 7,
      itemKey: 'stamina_bread',
      requestKey: expect.any(String),
    }));
    expect(postMock.mock.calls[1]?.[0]).toBe('/inventory/sell');
    expect(postMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      playerId: 7,
      itemKey: 'old_coin',
      quantity: 3,
      requestKey: expect.any(String),
    }));
  });

  it('batch operations include their full input and a request key', async () => {
    await inventoryApi.useItems(7, 'stamina_bread', 4);
    await inventoryApi.sellItems(7, [{ itemKey: 'old_coin', quantity: 2 }]);
    await inventoryApi.synthesize(7, 'make_tonic');
    await inventoryApi.synthesizeAll(7, 'make_tonic', 5);

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/inventory/use-batch',
      '/inventory/sell-batch',
      '/inventory/synthesis',
      '/inventory/synthesis-all',
    ]);
    for (const [, payload] of postMock.mock.calls) {
      expect(payload).toEqual(expect.objectContaining({ requestKey: expect.any(String) }));
    }
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({ quantity: 4 }));
    expect(postMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      items: [{ itemKey: 'old_coin', quantity: 2 }],
    }));
    expect(postMock.mock.calls[3]?.[1]).toEqual(expect.objectContaining({ times: 5 }));
  });

  it('reuses the same key after an uncertain request failure', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(inventoryApi.sellItem(7, 'old_coin', 2)).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await inventoryApi.sellItem(7, 'old_coin', 2);
    const retryPayload = postMock.mock.calls[1]?.[1] as { requestKey: string };

    expect(retryPayload.requestKey).toBe(firstPayload.requestKey);
  });

  it('world boss reward claims carry a stable request key', async () => {
    await worldBossApi.claim(7, 'boss-2026-09');

    expect(postMock.mock.calls[0]?.[0]).toBe('/world-boss/claim');
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({
      playerId: 7,
      bossNo: 'boss-2026-09',
      requestKey: expect.any(String),
    }));
  });

  it('quest reward claims reuse their key after a timeout and scope it to the quest cycle', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(questApi.claim(7, 'daily_explore_3', '2026-09-30'))
      .rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await questApi.claim(7, 'daily_explore_3', '2026-09-30');
    const retryPayload = postMock.mock.calls[1]?.[1] as { requestKey: string };
    await questApi.claim(7, 'daily_explore_3', '2026-10-01');
    const nextCyclePayload = postMock.mock.calls[2]?.[1] as { requestKey: string };

    expect(retryPayload.requestKey).toBe(firstPayload.requestKey);
    expect(nextCyclePayload.requestKey).not.toBe(firstPayload.requestKey);
    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/quests/claim', '/quests/claim', '/quests/claim',
    ]);
  });

  it('PK response requests carry a stable key for the challenge and defender', async () => {
    await pkApi.resolve(81, true, 7);

    expect(postMock.mock.calls[0]?.[0]).toBe('/pk/challenge/resolve');
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({
      challengeId: 81,
      accept: true,
      playerId: 7,
      requestKey: expect.any(String),
    }));
  });

  it('equipment enhancement reuses its key after a network timeout', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(equipmentApi.enhance(7, 'hunter_blade')).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await equipmentApi.enhance(7, 'hunter_blade');

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/equipment/enhance', '/equipment/enhance',
    ]);
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({
      playerId: 7,
      equipmentKey: 'hunter_blade',
      requestKey: expect.any(String),
    }));
    expect((postMock.mock.calls[1]?.[1] as { requestKey: string }).requestKey)
      .toBe(firstPayload.requestKey);
  });

  it('attribute reset requests carry a stable key and reuse it after timeout', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(playerApi.resetAllocation(7)).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await playerApi.resetAllocation(7);

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/player/reset-allocation', '/player/reset-allocation',
    ]);
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({
      playerId: 7,
      requestKey: expect.any(String),
    }));
    expect((postMock.mock.calls[1]?.[1] as { requestKey: string }).requestKey)
      .toBe(firstPayload.requestKey);
  });

  it('attribute allocation reuses its key for the same points after a timeout', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(playerApi.allocatePoints(7, 2, 1, 0, 0)).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await playerApi.allocatePoints(7, 2, 1, 0, 0);
    await playerApi.allocatePoints(7, 1, 1, 0, 0);

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/player/allocate-points', '/player/allocate-points', '/player/allocate-points',
    ]);
    expect(postMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({
      playerId: 7,
      atk: 2,
      def: 1,
      spd: 0,
      crit: 0,
      requestKey: expect.any(String),
    }));
    expect((postMock.mock.calls[1]?.[1] as { requestKey: string }).requestKey)
      .toBe(firstPayload.requestKey);
    expect((postMock.mock.calls[2]?.[1] as { requestKey: string }).requestKey)
      .not.toBe(firstPayload.requestKey);
  });

  it('self-revive requests carry a stable key and reuse it after timeout', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(playerApi.revive(7, 'coins')).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await playerApi.revive(7, 'coins');

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/player/revive', '/player/revive',
    ]);
    expect((postMock.mock.calls[1]?.[1] as { requestKey: string }).requestKey)
      .toBe(firstPayload.requestKey);
  });

  it('peer-revive requests carry a stable key and reuse it after timeout', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(playerApi.peerRevive(7, 9, 'coins')).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await playerApi.peerRevive(7, 9, 'coins');

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/player/peer-revive', '/player/peer-revive',
    ]);
    expect((postMock.mock.calls[1]?.[1] as { requestKey: string }).requestKey)
      .toBe(firstPayload.requestKey);
  });

  it('faction skill learning reuses the same request key after a timeout', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(skillApi.learnFactionSkill(7, 'abyss_gaze')).rejects.toThrow('network timeout');
    const firstPayload = postMock.mock.calls[0]?.[1] as { requestKey: string };
    await skillApi.learnFactionSkill(7, 'abyss_gaze');

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/skills/faction/learn', '/skills/faction/learn',
    ]);
    expect((postMock.mock.calls[1]?.[1] as { requestKey: string }).requestKey)
      .toBe(firstPayload.requestKey);
  });
});
