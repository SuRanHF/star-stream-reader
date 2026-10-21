import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { worldBossApi } from '@/api/worldBossApi';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('worldBossApi.attack', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('网络结果不确定时重试沿用同一请求键，成功后清除', async () => {
    const post = vi.mocked(http.post);
    post.mockRejectedValueOnce(new Error('network timeout'));
    post.mockResolvedValueOnce({ success: true });

    await expect(worldBossApi.attack(1, 'boss-1')).rejects.toThrow('network timeout');
    expect(localStorage.length).toBe(1);
    await expect(worldBossApi.attack(1, 'boss-1')).resolves.toEqual({ success: true });

    const firstBody = post.mock.calls[0][1] as Record<string, unknown>;
    const retryBody = post.mock.calls[1][1] as Record<string, unknown>;
    expect(firstBody.requestKey).toEqual(expect.any(String));
    expect(retryBody.requestKey).toBe(firstBody.requestKey);
    expect(firstBody.bossNo).toBe('boss-1');
    expect(localStorage.length).toBe(0);
  });

  it('不同角色或首领使用隔离的待确认请求键', async () => {
    const post = vi.mocked(http.post);
    post.mockResolvedValue({ success: true });

    await worldBossApi.attack(1, 'boss-1');
    await worldBossApi.attack(1, 'boss-2');
    await worldBossApi.attack(2, 'boss-1');

    const keys = post.mock.calls.map((call) => (call[1] as Record<string, unknown>).requestKey);
    expect(new Set(keys).size).toBe(3);
  });
});
