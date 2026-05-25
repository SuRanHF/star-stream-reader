import { http } from './http';
import type { ApiRecord } from '@/types/api';

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
  getRankings(bossNo: string) {
    return http.get<unknown, ApiRecord[]>(`/world-boss/rankings/${bossNo}`);
  },
  attack(playerId: number, bossNo?: string) {
    return http.post<unknown, ApiRecord>('/world-boss/attack', { playerId, bossNo });
  },
  claim(playerId: number, bossNo: string) {
    return http.post<unknown, ApiRecord>('/world-boss/claim', { playerId, bossNo });
  },
};
