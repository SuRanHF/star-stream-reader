import { http } from './http';
import type { GameBootstrap } from '@/types/bootstrap';

export const gameApi = {
  getBootstrap() {
    return http.get<unknown, GameBootstrap>('/game/bootstrap');
  },
};
