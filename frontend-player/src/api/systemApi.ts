import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const systemApi = {
  getVersion() {
    return http.get<unknown, ApiRecord>('/version');
  },
  getChangelog() {
    return http.get<unknown, ApiRecord[]>('/changelog');
  },
  getOnlineSummary() {
    return http.get<unknown, ApiRecord>('/online/summary');
  },
  getOnlinePlayers() {
    return http.get<unknown, ApiRecord[]>('/online/players');
  },
  submitFeedback(payload: ApiRecord) {
    return http.post<unknown, ApiRecord>('/feedback', payload);
  },
};
