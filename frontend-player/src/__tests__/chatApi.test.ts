import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { chatApi } from '@/api/chatApi';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('chatApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('世界频道不带可选参数时保持原有请求', async () => {
    await chatApi.getRecentMessages('world', 50);

    expect(http.get).toHaveBeenCalledWith('/chat/recent?channel=world&limit=50');
  });

  it('队伍频道带上玩家和分页游标，服务端据此解析归属', async () => {
    await chatApi.getRecentMessages('team', 50, { playerId: 11, beforeId: 340 });

    expect(http.get).toHaveBeenCalledWith('/chat/recent?channel=team&limit=50&playerId=11&beforeId=340');
  });

  it('回复时附带 replyToId，普通发送不带该字段', async () => {
    await chatApi.sendMessage(11, '收到', 'world', 42);
    await chatApi.sendMessage(11, '你好', 'world');

    expect(http.post).toHaveBeenNthCalledWith(1, '/chat/send', { playerId: 11, content: '收到', channel: 'world', replyToId: 42 });
    expect(http.post).toHaveBeenNthCalledWith(2, '/chat/send', { playerId: 11, content: '你好', channel: 'world' });
  });
});
