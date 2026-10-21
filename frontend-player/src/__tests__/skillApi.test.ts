import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { skillApi } from '@/api/skillApi';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('skillApi.unlock', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('请求超时后重试沿用同一个请求键，成功后清理', async () => {
    const post = vi.mocked(http.post);
    post.mockRejectedValueOnce(new Error('network timeout'));
    post.mockResolvedValueOnce({ skillKey: 'archive_pulse' });

    await expect(skillApi.unlock(1, 'archive_pulse')).rejects.toThrow('network timeout');
    expect(localStorage.length).toBe(1);
    await expect(skillApi.unlock(1, 'archive_pulse')).resolves.toEqual({ skillKey: 'archive_pulse' });

    const firstBody = post.mock.calls[0][1] as Record<string, unknown>;
    const retryBody = post.mock.calls[1][1] as Record<string, unknown>;
    expect(firstBody.requestKey).toEqual(expect.any(String));
    expect(retryBody.requestKey).toBe(firstBody.requestKey);
    expect(localStorage.length).toBe(0);
  });

  it('不同角色/技能组合不会共享待确认的请求键', async () => {
    const post = vi.mocked(http.post);
    post.mockResolvedValue({});

    await skillApi.unlock(1, 'archive_pulse');
    await skillApi.unlock(2, 'archive_pulse');

    const firstBody = post.mock.calls[0][1] as Record<string, unknown>;
    const secondBody = post.mock.calls[1][1] as Record<string, unknown>;
    expect(firstBody.requestKey).not.toBe(secondBody.requestKey);
  });
});
