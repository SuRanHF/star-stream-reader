import type { ApiRecord } from './api';

export type RealtimeStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'closed' | 'error';

export interface RealtimeEvent<T = ApiRecord> {
  type: string;
  data?: T;
  timestamp?: string;
}

export type RealtimeHandler<T = ApiRecord> = (event: RealtimeEvent<T>) => void;
