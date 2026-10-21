import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const factionApi = {
  getFactions(playerId?: number) {
    return http.get<unknown, ApiRecord[]>('/factions', { params: { playerId } });
  },
  getMyFaction(playerId: number) {
    return http.get<unknown, ApiRecord>(`/factions/my/${playerId}`);
  },
  getFactionSummary(playerId: number) {
    return http.get<unknown, ApiRecord>(`/factions/summary/${playerId}`);
  },
  getRankings() {
    return http.get<unknown, ApiRecord[]>('/factions/rankings');
  },
  join(playerId: number, factionKey: string) {
    return http.post<unknown, ApiRecord>('/factions/join', { playerId, factionKey });
  },
  leave(playerId: number) {
    return http.post<unknown, ApiRecord>('/factions/leave', { playerId });
  },
  getFactionSkills(factionKey: string) {
    return http.get<unknown, ApiRecord[]>(`/factions/${factionKey}/skills`);
  },
  getFactionWars() {
    return http.get<unknown, ApiRecord[]>('/factions/wars');
  },
  getFactionBuff(playerId: number) {
    return http.get<unknown, ApiRecord>(`/factions/buff/${playerId}`);
  },
};
