import { http } from './http';
import type { ApiRecord } from '@/types/api';

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
  startBattle(playerId: number, partyNo: string) {
    return http.post<unknown, ApiRecord>(`/party/${partyNo}/start-battle`, { playerId });
  },
};
