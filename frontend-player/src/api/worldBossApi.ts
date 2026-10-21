import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const worldBossApi = {
  getWorldBossSummary(playerId: number) {
    return http.get<unknown, ApiRecord>(`/world-boss/summary/${playerId}`);
  },
  getActiveWorldBoss(playerId?: number) {
    return http.get<unknown, ApiRecord>('/world-boss/active', { params: { playerId } });
  },
  getMyParticipation(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/world-boss/my/${playerId}`);
  },
  getHistory() {
    return http.get<unknown, ApiRecord[]>('/world-boss/history');
  },
  getRankings(bossNo: string) {
    return http.get<unknown, ApiRecord[]>(`/world-boss/rankings/${bossNo}`);
  },
  attack(playerId: number, bossNo?: string) {
    return withStableActionRequest(`world-boss:attack:${playerId}:${bossNo ?? 'active'}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/world-boss/attack', { playerId, bossNo, requestKey }));
  },
  claim(playerId: number, bossNo: string) {
    return withStableActionRequest(`world-boss:claim:${playerId}:${bossNo}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/world-boss/claim', { playerId, bossNo, requestKey }));
  },
};
