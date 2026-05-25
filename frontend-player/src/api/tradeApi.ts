import { http } from './http';
import type { ApiRecord } from '@/types/api';

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
    return http.post<unknown, ApiRecord>('/trade/buy', { buyerPlayerId, listingNo });
  },
  cancel(sellerPlayerId: number, listingNo: string) {
    return http.post<unknown, ApiRecord>('/trade/cancel', { sellerPlayerId, listingNo });
  },
};
