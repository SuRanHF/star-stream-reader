import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const broadcastApi = {
  getActive() {
    return http.get<unknown, ApiRecord[]>('/broadcast/active');
  },
  getRankings(limit = 50) {
    return http.get<unknown, ApiRecord[]>('/broadcast/rankings', { params: { limit } });
  },
  getMine(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/broadcast/my/${playerId}`);
  },
  claim(playerId: number, eventKey: string) {
    return http.post<unknown, ApiRecord>('/broadcast/claim', { playerId, eventKey });
  },
};
