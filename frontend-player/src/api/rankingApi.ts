import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const rankingApi = {
  getRankings(limit = 50) {
    return http.get<unknown, ApiRecord[]>('/rankings', { params: { limit } });
  },
  getStoryDiscoveries(limit = 100) {
    return http.get<unknown, ApiRecord[]>('/rankings/story-discoveries', { params: { limit } });
  },
  getStoryDiscoveryPosition(playerId: number) {
    return http.get<unknown, ApiRecord>(`/rankings/story-discoveries/${playerId}`);
  },
  getPkPerformanceRankings(limit = 100) {
    return http.get<unknown, ApiRecord[]>('/rankings/pk-performance', { params: { limit } });
  },
  getPkPerformancePosition(playerId: number) {
    return http.get<unknown, ApiRecord>(`/rankings/pk-performance/${playerId}`);
  },
  getExploreActionRankings(limit = 100) {
    return http.get<unknown, ApiRecord[]>('/rankings/exploration-actions', { params: { limit } });
  },
  getExploreActionPosition(playerId: number) {
    return http.get<unknown, ApiRecord>(`/rankings/exploration-actions/${playerId}`);
  },
  getMyRank(playerId: number) {
    return http.get<unknown, ApiRecord>(`/rankings/${playerId}`);
  },
  getNearbyRankings(playerId: number, radius = 2) {
    return http.get<unknown, ApiRecord[]>(`/rankings/${playerId}/nearby`, { params: { radius } });
  },
  getAvatarRankInfo(playerId: number) {
    return http.get<unknown, ApiRecord>(`/avatar-rank/${playerId}`);
  },
  getAvatarLeaderboard(limit = 200) {
    return http.get<unknown, ApiRecord[]>('/avatar-rank/leaderboard', { params: { limit } });
  },
  rankUp(playerId: number) {
    return http.post<unknown, ApiRecord>(`/avatar-rank/${playerId}/rank-up`, { playerId });
  },
  prestige(playerId: number) {
    return http.post<unknown, ApiRecord>(`/avatar-rank/${playerId}/prestige`, {});
  },
};
