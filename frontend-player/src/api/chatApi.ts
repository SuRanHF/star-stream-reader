import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const chatApi = {
  getRecentMessages() {
    return http.get<unknown, ApiRecord[]>('/chat/recent');
  },
  sendMessage(playerId: number, content: string, channel = 'world') {
    return http.post<unknown, ApiRecord>('/chat/send', { playerId, content, channel });
  },
};
