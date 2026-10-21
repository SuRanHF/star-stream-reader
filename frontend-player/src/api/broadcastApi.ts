import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const broadcastApi = {
  getActive() {
    return http.get<unknown, ApiRecord[]>('/broadcast/active');
  },
  getHistory(limit = 12) {
    return http.get<unknown, ApiRecord[]>('/broadcast/history', { params: { limit } });
  },
  getRankings(eventKey: string, limit = 10) {
    return http.get<unknown, ApiRecord[]>('/broadcast/rankings', { params: { eventKey, limit } });
  },
  getMyRankingPosition(eventKey: string, playerId: number) {
    return http.get<unknown, ApiRecord>(
      `/broadcast/rankings/${encodeURIComponent(eventKey)}/me/${playerId}`,
    );
  },
  getMine(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/broadcast/my/${playerId}`);
  },
  claim(playerId: number, eventKey: string) {
    return http.post<unknown, ApiRecord>('/broadcast/claim', { playerId, eventKey });
  },
};
