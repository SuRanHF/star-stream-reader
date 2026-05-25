import { http } from './http';
import type { ApiRecord } from '@/types/api';

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
  unlock(playerId: number, skillKey: string) {
    return http.post<unknown, ApiRecord>('/skills/unlock', { playerId, skillKey });
  },
  equip(playerId: number, skillKey: string) {
    return http.post<unknown, ApiRecord>('/skills/equip', { playerId, skillKey });
  },
  unequip(playerId: number, skillKey: string) {
    return http.post<unknown, ApiRecord>('/skills/unequip', { playerId, skillKey });
  },
};
