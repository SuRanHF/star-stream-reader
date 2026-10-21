import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const inventoryApi = {
  getInventory(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/inventory/${playerId}`);
  },
  useItem(playerId: number, itemKey: string) {
    return withStableActionRequest(`inventory:use:${playerId}:${itemKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/inventory/use', { playerId, itemKey, requestKey }));
  },
  useItems(playerId: number, itemKey: string, quantity: number) {
    return withStableActionRequest(`inventory:use-batch:${playerId}:${itemKey}:${quantity}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/inventory/use-batch', { playerId, itemKey, quantity, requestKey }));
  },
  sellItem(playerId: number, itemKey: string, quantity = 1) {
    return withStableActionRequest(`inventory:sell:${playerId}:${itemKey}:${quantity}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/inventory/sell', { playerId, itemKey, quantity, requestKey }));
  },
  sellItems(playerId: number, items: Array<{ itemKey: string; quantity: number }>) {
    const scope = JSON.stringify(items.map(({ itemKey, quantity }) => [itemKey, quantity]));
    return withStableActionRequest(`inventory:sell-batch:${playerId}:${scope}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/inventory/sell-batch', { playerId, items, requestKey }));
  },
  getRecipes() {
    return http.get<unknown, ApiRecord>('/inventory/synthesis/recipes');
  },
  synthesize(playerId: number, recipeKey: string) {
    return withStableActionRequest(`inventory:synthesis:${playerId}:${recipeKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/inventory/synthesis', { playerId, recipeKey, requestKey }));
  },
  synthesizeAll(playerId: number, recipeKey: string, times: number) {
    return withStableActionRequest(`inventory:synthesis-all:${playerId}:${recipeKey}:${times}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/inventory/synthesis-all', { playerId, recipeKey, times, requestKey }));
  },
};
