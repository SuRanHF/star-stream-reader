import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const narrativeApi = {
  checkGhost(playerId: number, locationKey: string) {
    return http.get<unknown, ApiRecord>(`/narrative/ghost-check/${playerId}/${locationKey}`);
  },
  processEncounter(playerId: number, ghostKey: string, nodeIndex: number, choiceIndex: number) {
    const scope = `narrative:ghost-choice:${playerId}:${ghostKey}:${nodeIndex}:${choiceIndex}`;
    return withStableActionRequest(scope, (requestKey) =>
      http.post<unknown, ApiRecord>('/narrative/ghost-encounter', {
        playerId, ghostKey, nodeIndex, choiceIndex, requestKey,
      }));
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
