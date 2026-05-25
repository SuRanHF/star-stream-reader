import { http } from './http';
import type { ApiRecord } from '@/types/api';
import type { QuestSummary } from '@/types/bootstrap';

export const questApi = {
  getQuestSummary(playerId: number) {
    return http.get<unknown, QuestSummary>(`/quests/summary/${playerId}`);
  },
  getQuests(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/quests/${playerId}`);
  },
  getDefinitions() {
    return http.get<unknown, ApiRecord[]>('/quests/definitions');
  },
  claim(playerId: number, questKey: string, cycleKey?: string) {
    return http.post<unknown, ApiRecord>('/quests/claim', { playerId, questKey, cycleKey });
  },
  refresh(playerId: number) {
    return http.post<unknown, ApiRecord[]>('/quests/refresh', { playerId });
  },
};
