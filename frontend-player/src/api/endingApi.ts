import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const endingApi = {
  getEndings(playerId: number) {
    return http.get<unknown, ApiRecord>(`/endings/${playerId}`);
  },
};
