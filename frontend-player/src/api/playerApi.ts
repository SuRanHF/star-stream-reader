import { http } from './http';
import type { ApiRecord } from '@/types/api';
import type { Player } from '@/types/player';

export const playerApi = {
  createPlayer(payload: { playerName: string }) {
    return http.post<unknown, Player>('/auth/create-player', payload);
  },
  getPlayer(playerId: number) {
    return http.get<unknown, Player>(`/player/${playerId}`);
  },
  switchLocation(playerId: number, locationKey: string) {
    return http.post<unknown, ApiRecord>('/player/switch-location', { playerId, locationKey });
  },
  getRestState(playerId: number) {
    return http.get<unknown, ApiRecord>('/player/rest/state', { params: { playerId } });
  },
  startRest(playerId: number) {
    return http.post<unknown, ApiRecord>('/player/rest/start', { playerId });
  },
  stopRest(playerId: number) {
    return http.post<unknown, ApiRecord>('/player/rest/stop', { playerId });
  },
  getDeadList() {
    return http.get<unknown, ApiRecord[]>('/player/dead-list');
  },
  revive(playerId: number, method = 'coins') {
    return http.post<unknown, ApiRecord>('/player/revive', { playerId, method });
  },
  peerRevive(reviverId: number, targetId: number, method = 'coins') {
    return http.post<unknown, ApiRecord>('/player/peer-revive', { reviverId, targetId, method });
  },
  getConstellations() {
    return http.get<unknown, ApiRecord>('/player/constellations');
  },
  selectConstellation(playerId: number, constellationKey: string) {
    return http.post<unknown, ApiRecord>('/player/select-constellation', { playerId, constellationKey });
  },
  changeConstellation(playerId: number, constellationKey: string) {
    return http.post<unknown, ApiRecord>('/player/change-constellation', { playerId, constellationKey });
  },
  allocatePoints(playerId: number, atk: number, def: number, spd: number, crit: number) {
    return http.post<unknown, Player>('/player/allocate-points', { playerId, atk, def, spd, crit });
  },
  resetAllocation(playerId: number) {
    return http.post<unknown, Player>('/player/reset-allocation', { playerId });
  },
};
