import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import { http } from '@/api/http';
import { narrativeApi } from '@/api/narrativeApi';

const postMock = vi.mocked(http.post);

describe('narrativeApi request keys', () => {
  beforeEach(() => {
    localStorage.clear();
    postMock.mockReset();
    postMock.mockResolvedValue({ data: { success: true, data: {} } } as never);
  });

  it('相同残影节点选项超时重试复用请求键，成功后不同选项使用新键', async () => {
    postMock.mockRejectedValueOnce(new Error('network timeout'));

    await expect(narrativeApi.processEncounter(7, 'reader_ghost', 2, 1))
      .rejects.toThrow('network timeout');
    await narrativeApi.processEncounter(7, 'reader_ghost', 2, 1);
    await narrativeApi.processEncounter(7, 'reader_ghost', 2, 0);

    expect(postMock.mock.calls.map(([url]) => url)).toEqual([
      '/narrative/ghost-encounter',
      '/narrative/ghost-encounter',
      '/narrative/ghost-encounter',
    ]);
    const firstPayload = postMock.mock.calls[0]?.[1] as Record<string, unknown>;
    const retryPayload = postMock.mock.calls[1]?.[1] as Record<string, unknown>;
    const otherChoicePayload = postMock.mock.calls[2]?.[1] as Record<string, unknown>;

    expect(firstPayload).toEqual(expect.objectContaining({
      playerId: 7,
      ghostKey: 'reader_ghost',
      nodeIndex: 2,
      choiceIndex: 1,
      requestKey: expect.any(String),
    }));
    expect(retryPayload.requestKey).toBe(firstPayload.requestKey);
    expect(otherChoicePayload.requestKey).not.toBe(firstPayload.requestKey);
  });
});
