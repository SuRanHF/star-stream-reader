import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const equipmentApi = {
  getPlayerEquipment(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/equipment/${playerId}`);
  },
  getEquipped(playerId: number) {
    return http.get<unknown, ApiRecord>(`/equipment/equipped/${playerId}`);
  },
  getBonus(playerId: number) {
    return http.get<unknown, ApiRecord>(`/equipment/bonus/${playerId}`);
  },
  getSets() {
    return http.get<unknown, ApiRecord[]>('/equipment/sets');
  },
  getMySetBonuses(playerId: number) {
    return http.get<unknown, ApiRecord>(`/equipment/sets/my/${playerId}`);
  },
  equip(playerId: number, equipmentKey: string) {
    return http.post<unknown, ApiRecord>('/equipment/equip', { playerId, equipmentKey });
  },
  unequip(playerId: number, equipmentKey?: string, slot?: string) {
    return http.post<unknown, ApiRecord>('/equipment/unequip', { playerId, equipmentKey, slot });
  },
  repair(playerId: number, equipmentKey?: string, slot?: string) {
    return http.post<unknown, ApiRecord>('/equipment/repair', { playerId, equipmentKey, slot });
  },
  enhance(playerId: number, equipmentKey: string) {
    return withStableActionRequest(`equipment:enhance:${playerId}:${equipmentKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/equipment/enhance', { playerId, equipmentKey, requestKey }));
  },
  repairAll(playerId: number) {
    return http.post<unknown, ApiRecord>('/equipment/repair-all', { playerId });
  },
};
