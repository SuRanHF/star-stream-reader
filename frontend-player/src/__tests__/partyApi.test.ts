import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { partyApi } from '@/api/partyApi';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('partyApi.startBattle', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('网络超时后沿用同一请求键，成功收到回执后清除', async () => {
    const post = vi.mocked(http.post);
    post.mockRejectedValueOnce(new Error('network timeout'));
    post.mockResolvedValueOnce({ bossNo: 'WB-H2' });

    await expect(partyApi.startBattle(7, 'PT-7')).rejects.toThrow('network timeout');
    expect(localStorage.length).toBe(1);
    await expect(partyApi.startBattle(7, 'PT-7')).resolves.toEqual({ bossNo: 'WB-H2' });

    expect(post).toHaveBeenNthCalledWith(1, '/party/PT-7/start-battle', {
      playerId: 7,
      requestKey: expect.any(String),
    });
    expect(post.mock.calls[1][1]).toEqual(post.mock.calls[0][1]);
    expect(localStorage.length).toBe(0);
  });

  it('不同角色或队伍的待确认讨伐请求键相互隔离', async () => {
    const post = vi.mocked(http.post);
    post.mockResolvedValue({});

    await partyApi.startBattle(7, 'PT-7');
    await partyApi.startBattle(7, 'PT-8');
    await partyApi.startBattle(8, 'PT-7');

    const requestKeys = post.mock.calls.map((call) => (call[1] as Record<string, unknown>).requestKey);
    expect(new Set(requestKeys).size).toBe(3);
  });
});
