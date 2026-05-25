import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const bountyApi = {
  getPending() {
    return http.get<unknown, ApiRecord[]>('/bounty/pending');
  },
  getMine(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/bounty/my/${playerId}`);
  },
  publish(payload: { playerId: number; monsterKey?: string; locationKey?: string; monsterName?: string; sharePercent?: number }) {
    return http.post<unknown, ApiRecord>('/bounty/publish', payload);
  },
  accept(bountyId: number, playerId: number) {
    return http.post<unknown, ApiRecord>(`/bounty/accept/${bountyId}`, { playerId });
  },
  cancel(playerId: number) {
    return http.post<unknown, ApiRecord>('/bounty/cancel', { playerId });
  },
};
