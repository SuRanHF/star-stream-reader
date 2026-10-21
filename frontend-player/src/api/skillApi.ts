import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const skillApi = {
  getAllSkills() {
    return http.get<unknown, ApiRecord[]>('/skills/all');
  },
  getPlayerSkills(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/skills/${playerId}`);
  },
  getUnlockable(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/skills/unlockable/${playerId}`);
  },
  getBonus(playerId: number) {
    return http.get<unknown, ApiRecord>(`/skills/bonus/${playerId}`);
  },
  getFactionPlayerSkills(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/skills/faction/player/${playerId}`);
  },
  unlock(playerId: number, skillKey: string) {
    return withStableActionRequest(`skill-unlock:${playerId}:${skillKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/skills/unlock', { playerId, skillKey, requestKey }));
  },
  equip(playerId: number, skillKey: string) {
    return http.post<unknown, ApiRecord>('/skills/equip', { playerId, skillKey });
  },
  unequip(playerId: number, skillKey: string) {
    return http.post<unknown, ApiRecord>('/skills/unequip', { playerId, skillKey });
  },
  learnFactionSkill(playerId: number, skillKey: string) {
    return withStableActionRequest(`faction-skill:learn:${playerId}:${skillKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/skills/faction/learn', { playerId, skillKey, requestKey }));
  },
};
