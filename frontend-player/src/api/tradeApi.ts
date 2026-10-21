import { http } from './http';
import type { ApiRecord } from '@/types/api';
import { withStableActionRequest } from '@/utils/stableActionRequest';

export const tradeApi = {
  getListings() {
    return http.get<unknown, ApiRecord>('/trade/listings');
  },
  getMyListings(playerId: number, status?: string) {
    return http.get<unknown, ApiRecord[]>(`/trade/my/${playerId}`, { params: { status } });
  },
  getRecords(playerId: number) {
    return http.get<unknown, ApiRecord[]>(`/trade/records/${playerId}`);
  },
  getSummary(playerId: number) {
    return http.get<unknown, ApiRecord>(`/trade/summary/${playerId}`);
  },
  buy(buyerPlayerId: number, listingNo: string) {
    return withStableActionRequest(`trade:buy:${buyerPlayerId}:${listingNo}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/trade/buy', { buyerPlayerId, listingNo, requestKey }));
  },
  cancel(sellerPlayerId: number, listingNo: string) {
    return withStableActionRequest(`trade:cancel:${sellerPlayerId}:${listingNo}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/trade/cancel', { sellerPlayerId, listingNo, requestKey }));
  },
  createItemListing(sellerPlayerId: number, itemKey: string, quantity: number, unitPrice: number) {
    return withStableActionRequest(`trade:list:item:${sellerPlayerId}:${itemKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/trade/list/item', {
        sellerPlayerId, itemKey, quantity, unitPrice, requestKey,
      }));
  },
  createEquipmentListing(sellerPlayerId: number, equipmentKey: string, unitPrice: number) {
    return withStableActionRequest(`trade:list:equipment:${sellerPlayerId}:${equipmentKey}`, (requestKey) =>
      http.post<unknown, ApiRecord>('/trade/list/equipment', {
        sellerPlayerId, equipmentKey, unitPrice, requestKey,
      }));
  },
};
