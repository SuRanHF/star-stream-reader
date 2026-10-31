import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useChatStore } from '@/stores/chatStore';
import { useRealtimeStore } from '@/stores/realtimeStore';

describe('chatStore.pruneChannel', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('剔除同频道中比最新历史还旧、却不在历史里的实时消息（换队残留），其他频道不动', () => {
    const chat = useChatStore();
    chat.addMessage({ id: 10, channel: 'team', content: '旧队消息' });
    chat.addMessage({ id: 21, channel: 'team', content: '当前队消息' });
    chat.addMessage({ id: 30, channel: 'team', content: '历史之后刚收到的' });
    chat.addMessage({ id: 11, channel: 'world', content: '世界消息' });

    chat.pruneChannel('team', [20, 21]);

    expect(chat.messages.map((m) => m.id)).toEqual([21, 30, 11]);
  });

  it('历史为空（已离队）时清掉该频道全部实时消息', () => {
    const chat = useChatStore();
    chat.addMessage({ id: 5, channel: 'faction', content: '旧阵营' });
    chat.addMessage({ id: 6, channel: 'world', content: '世界' });

    chat.pruneChannel('faction', []);

    expect(chat.messages.map((m) => m.id)).toEqual([6]);
  });

  it('clear 清空实时与系统消息', () => {
    const chat = useChatStore();
    chat.addMessage({ id: 1, channel: 'world' });
    chat.addSystemMessage({ id: 2, channel: 'system' });

    chat.clear();

    expect(chat.messages).toEqual([]);
    expect(chat.systemMessages).toEqual([]);
  });
});

describe('realtimeStore.onlineCount', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('只接受非负数字，缺失值不当成 0', () => {
    const rt = useRealtimeStore();
    rt.setOnlineCount(174);
    expect(rt.onlineCount).toBe(174);
    rt.setOnlineCount(null);
    expect(rt.onlineCount).toBeNull();
    rt.setOnlineCount(-1);
    expect(rt.onlineCount).toBeNull();
  });

  it('断线或出错后清空在线人数，避免显示过期数字', () => {
    const rt = useRealtimeStore();
    rt.setStatus('connected');
    rt.setOnlineCount(3);
    rt.setStatus('closed');
    expect(rt.onlineCount).toBeNull();

    rt.setStatus('connected');
    rt.setOnlineCount(3);
    rt.setError('WebSocket 连接异常');
    expect(rt.onlineCount).toBeNull();
  });
});
