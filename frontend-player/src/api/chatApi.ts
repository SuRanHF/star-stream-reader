import { http } from './http';
import type { ApiRecord } from '@/types/api';

export interface RecentChatOptions {
  // 队伍 / 阵营频道必须带上当前玩家，服务端据此解析归属。
  playerId?: number;
  // 加载更早：返回 id 小于它的消息。
  beforeId?: number;
}

export const chatApi = {
  getRecentMessages(channel = 'world', limit = 50, options: RecentChatOptions = {}) {
    const params = new URLSearchParams({ channel, limit: String(limit) });
    if (options.playerId) params.set('playerId', String(options.playerId));
    if (options.beforeId) params.set('beforeId', String(options.beforeId));
    return http.get<unknown, ApiRecord[]>(`/chat/recent?${params.toString()}`);
  },
  sendMessage(playerId: number, content: string, channel = 'world', replyToId?: number) {
    return http.post<unknown, ApiRecord>('/chat/send', { playerId, content, channel, ...(replyToId ? { replyToId } : {}) });
  },
};
