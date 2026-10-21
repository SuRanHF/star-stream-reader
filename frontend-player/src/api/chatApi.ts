import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const chatApi = {
  getRecentMessages(channel = 'world', limit = 50) {
    return http.get<unknown, ApiRecord[]>(`/chat/recent?channel=${encodeURIComponent(channel)}&limit=${limit}`);
  },
  sendMessage(playerId: number, content: string, channel = 'world') {
    return http.post<unknown, ApiRecord>('/chat/send', { playerId, content, channel });
  },
};
