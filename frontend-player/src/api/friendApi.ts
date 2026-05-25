import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const friendApi = {
  getFriends(playerId: number) {
    return http.get<unknown, ApiRecord[]>('/friends/list', { params: { playerId } });
  },
  getRequests(playerId: number) {
    return http.get<unknown, ApiRecord[]>('/friends/requests', { params: { playerId } });
  },
  getSummary(playerId: number) {
    return http.get<unknown, ApiRecord>('/friends/summary', { params: { playerId } });
  },
  search(keyword: string, playerId: number) {
    return http.get<unknown, ApiRecord[]>('/friends/search', { params: { keyword, playerId } });
  },
  request(playerId: number, targetPlayerId: number) {
    return http.post<unknown, ApiRecord>('/friends/request', { playerId, targetPlayerId });
  },
  accept(playerId: number, requestId: number) {
    return http.post<unknown, ApiRecord>('/friends/accept', { playerId, requestId });
  },
  reject(playerId: number, requestId: number) {
    return http.post<unknown, ApiRecord>('/friends/reject', { playerId, requestId });
  },
  remove(playerId: number, targetPlayerId: number) {
    return http.post<unknown, ApiRecord>('/friends/remove', { playerId, targetPlayerId });
  },
  gift(playerId: number, targetId: number, itemKey?: string) {
    return http.post<unknown, ApiRecord>('/friends/gift', { playerId, targetId, itemKey });
  },
};
