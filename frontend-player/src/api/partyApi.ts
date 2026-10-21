import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const partyApi = {
  getActiveParties() {
    return http.get<unknown, ApiRecord[]>('/party/active');
  },
  getMyParty(playerId: number) {
    return http.get<unknown, ApiRecord>(`/party/my/${playerId}`);
  },
  getSummary(playerId: number) {
    return http.get<unknown, ApiRecord>(`/party/summary/${playerId}`);
  },
  create(playerId: number, name: string, description?: string) {
    return http.post<unknown, ApiRecord>('/party/create', { playerId, name, description });
  },
  join(playerId: number, partyNo: string) {
    return http.post<unknown, ApiRecord>('/party/join', { playerId, partyNo });
  },
  leave(playerId: number) {
    return http.post<unknown, ApiRecord>('/party/leave', { playerId });
  },
  setReady(playerId: number, partyNo: string, ready: boolean) {
    return http.post<unknown, ApiRecord>(`/party/${partyNo}/ready`, { playerId, ready });
  },
  startBattle(playerId: number, partyNo: string) {
    return withStableActionRequest(`party:world-boss:attack:${playerId}:${partyNo}`, (requestKey) =>
      http.post<unknown, ApiRecord>(`/party/${partyNo}/start-battle`, { playerId, requestKey }));
  },
};
