import type { ExploreResult } from '@/api/exploreApi';

const STORAGE_PREFIX = 'reader-game:pending-combat:v1:';
const inMemoryKeys = new Map<string, string>();

function createRequestKey(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  const bytes = new Uint8Array(16);
  if (typeof globalThis.crypto?.getRandomValues === 'function') globalThis.crypto.getRandomValues(bytes);
  else bytes.forEach((_, index) => { bytes[index] = Math.floor(Math.random() * 256); });
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function fallbackStorageKey(playerId: number, result: ExploreResult): string {
  const eventKey = result.result?.event_key || result.monster_key || 'unknown';
  return `${STORAGE_PREFIX}${playerId}:${encodeURIComponent(eventKey)}`;
}

/**
 * Use the durable exploration request key when available, so server-restored
 * encounters replay the same combat action on any reload or device. Older
 * encounters without that key keep a local fallback until they are settled.
 */
export function getPendingCombatRequestKey(playerId: number, result: ExploreResult): string {
  if (typeof result.request_key === 'string' && result.request_key.length > 0
      && result.request_key.length <= 64) return result.request_key;

  const key = fallbackStorageKey(playerId, result);
  try {
    const existing = globalThis.localStorage.getItem(key);
    if (existing && existing.length <= 64) return existing;
    const next = createRequestKey();
    globalThis.localStorage.setItem(key, next);
    inMemoryKeys.set(key, next);
    return next;
  } catch {
    const existing = inMemoryKeys.get(key);
    if (existing) return existing;
    const next = createRequestKey();
    inMemoryKeys.set(key, next);
    return next;
  }
}

/** Clear only the legacy fallback key associated with this encounter. */
export function clearPendingCombatRequestKey(
  playerId: number,
  result: ExploreResult,
  requestKey: string,
): void {
  if (result.request_key) return;
  const key = fallbackStorageKey(playerId, result);
  if (inMemoryKeys.get(key) === requestKey) inMemoryKeys.delete(key);
  try {
    if (globalThis.localStorage.getItem(key) === requestKey) globalThis.localStorage.removeItem(key);
  } catch {
    // Storage may be disabled; the in-memory fallback is still cleared above.
  }
}
