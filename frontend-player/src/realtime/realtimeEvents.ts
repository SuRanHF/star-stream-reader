export const REALTIME_EVENTS = [
  'chat.message',
  'system.message',
  'online.summary',
  'pk.challenge',
  'pk.result',
  'party.updated',
  'trade.listing.sold',
  'worldBoss.hp.updated',
  'broadcast.progress.updated',
  'worldline.updated',
  'quest.completed',
  'faction.contribution.updated',
  'stats.updated',
] as const;

export type RealtimeEventType = (typeof REALTIME_EVENTS)[number];
