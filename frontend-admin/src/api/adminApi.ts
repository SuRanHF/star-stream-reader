import { http } from './http';

export interface ApiRecord {
  [key: string]: unknown;
}

export const adminApi = {
  // Players
  listPlayers(params?: { search?: string; limit?: number; offset?: number }) {
    return http.get<unknown, { players: ApiRecord[]; total: number }>('/admin/players', { params });
  },
  getPlayer(id: number) {
    return http.get<unknown, { player: ApiRecord }>(`/admin/players/${id}`);
  },
  updatePlayer(id: number, body: ApiRecord) {
    return http.post<unknown, ApiRecord>(`/admin/players/${id}/update`, body);
  },
  forceRevive(id: number) {
    return http.post<unknown, ApiRecord>(`/admin/players/${id}/force-revive`, {});
  },
  grant(id: number, body: { type: string; key: string; quantity?: number }) {
    return http.post<unknown, ApiRecord>(`/admin/players/${id}/grant`, body);
  },
  quickAction(id: number, action: string) {
    return http.post<unknown, ApiRecord>(`/admin/players/${id}/quick-action`, { action });
  },
  getPlayerLogs(id: number, limit?: number) {
    return http.get<unknown, { logs: string[]; total: number }>(`/admin/players/${id}/logs`, { params: { limit } });
  },
  getGrantOptions() {
    return http.get<unknown, ApiRecord>('/admin/grant-options');
  },
  getProgressOptions() {
    return http.get<unknown, ApiRecord>('/admin/progress-options');
  },
  // Feedback
  getFeedback(params?: { status?: string }) {
    return http.get<unknown, { feedback: ApiRecord[]; total: number }>('/admin/feedback', { params });
  },
  updateFeedback(id: number, body: { status?: string; note?: string }) {
    return http.patch<unknown, ApiRecord>(`/admin/feedback/${id}`, body);
  },
  // Actions log
  getActions(limit?: number) {
    return http.get<unknown, { actions: ApiRecord[]; total: number }>('/admin/actions', { params: { limit } });
  },
  // Titles
  getTitles() {
    return http.get<unknown, { titles: ApiRecord[] }>('/admin/titles');
  },
  modifyPlayerTitle(id: number, action: 'grant' | 'revoke', titleKey: string) {
    return http.post<unknown, ApiRecord>(`/admin/players/${id}/titles`, { action, title_key: titleKey });
  },
};
