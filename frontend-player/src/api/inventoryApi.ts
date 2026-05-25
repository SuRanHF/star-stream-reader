import { http } from './http';
import type { ApiRecord } from '@/types/api';

export const inventoryApi = {
  getInventory(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/inventory/${playerId}`);
  },
  useItem(playerId: number, itemKey: string) {
    return http.post<unknown, ApiRecord>('/inventory/use', { playerId, itemKey });
  },
  sellItem(playerId: number, itemKey: string, quantity = 1) {
    return http.post<unknown, ApiRecord>('/inventory/sell', { playerId, itemKey, quantity });
  },
  getRecipes() {
    return http.get<unknown, ApiRecord>('/inventory/synthesis/recipes');
  },
  synthesize(playerId: number, recipeKey: string) {
    return http.post<unknown, ApiRecord>('/inventory/synthesis', { playerId, recipeKey });
  },
};
