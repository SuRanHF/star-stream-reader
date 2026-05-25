import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const narrativeApi = {
  checkGhost(playerId: number, locationKey: string) {
    return http.get<unknown, ApiRecord>(`/narrative/ghost-check/${playerId}/${locationKey}`);
  },
  processEncounter(playerId: number, ghostKey: string, nodeIndex: number, choiceIndex: number) {
    return http.post<unknown, ApiRecord>('/narrative/ghost-encounter', { playerId, ghostKey, nodeIndex, choiceIndex });
  },
  getItemMemories(itemKey?: string) {
    if (itemKey) {
      return http.get<unknown, ApiRecord[]>(`/narrative/item-memories/${itemKey}`);
    }
    return http.get<unknown, ApiRecord[]>('/narrative/item-memories');
  },
  getEncounters(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/narrative/encounters/${playerId}`);
  },
};
