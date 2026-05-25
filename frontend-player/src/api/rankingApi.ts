import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const rankingApi = {
  getRankings(limit = 50) {
    return http.get<unknown, ApiRecord[]>('/rankings', { params: { limit } });
  },
  getMyRank(playerId: number) {
    return http.get<unknown, ApiRecord>(`/rankings/${playerId}`);
  },
  getAvatarRankInfo(playerId: number) {
    return http.get<unknown, ApiRecord>(`/avatar-rank/${playerId}`);
  },
  getAvatarLeaderboard(limit = 50) {
    return http.get<unknown, ApiRecord[]>('/avatar-rank/leaderboard', { params: { limit } });
  },
  rankUp(playerId: number) {
    return http.post<unknown, ApiRecord>(`/avatar-rank/${playerId}/rank-up`, { playerId });
  },
  prestige(playerId: number) {
    return http.post<unknown, ApiRecord>(`/avatar-rank/${playerId}/prestige`, {});
  },
};
