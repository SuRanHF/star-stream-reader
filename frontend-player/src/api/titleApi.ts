import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const titleApi = {
  getAll() {
    return http.get<unknown, ApiRecord[]>('/titles/all');
  },
  getMine(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/titles/my/${playerId}`);
  },
  getEquipped(playerId: number) {
    return http.get<unknown, ApiRecord>(`/titles/equipped/${playerId}`);
  },
  getEffects(playerId: number) {
    return http.get<unknown, ApiRecord>(`/titles/effects/${playerId}`);
  },
  evaluate(playerId: number) {
    return http.post<unknown, ApiRecord[]>(`/titles/evaluate/${playerId}`);
  },
  equip(playerId: number, titleKey: string) {
    return http.post<unknown, ApiRecord>('/titles/equip', { playerId, titleKey });
  },
};
