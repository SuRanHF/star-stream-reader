import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const combatApi = {
  getMonsters() {
    return http.get<unknown, ApiRecord[]>('/combat/monsters');
  },
  getMonstersByLocation(locationKey: string) {
    return http.get<unknown, ApiRecord[]>(`/combat/monsters/by-location/${locationKey}`);
  },
};
