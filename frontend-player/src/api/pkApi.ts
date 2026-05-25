import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const pkApi = {
  getOpponents(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/pk/opponents/${playerId}`);
  },
  getRecords(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/pk/records/${playerId}`);
  },
  challenge(attackerId: number, defenderId: number, mode = 'spar') {
    return http.post<unknown, ApiRecord>('/pk/challenge', { attackerId, defenderId, mode });
  },
  resolve(challengeId: number, accept: boolean, playerId: number) {
    return http.post<unknown, ApiRecord>('/pk/challenge/resolve', { challengeId, accept, playerId });
  },
};
