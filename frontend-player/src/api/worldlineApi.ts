import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const worldlineApi = {
  getStates() {
    return http.get<unknown, ApiRecord[]>('/worldline/states');
  },
  getSummary() {
    return http.get<unknown, ApiRecord>('/worldline/summary');
  },
  getHistory() {
    return http.get<unknown, ApiRecord[]>('/worldline/history');
  },
};
