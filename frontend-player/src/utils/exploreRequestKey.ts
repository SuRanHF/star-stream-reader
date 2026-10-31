const pendingKeys = new Map<string, string>();
const STORAGE_PREFIX = 'reader-game:pending-explore:v1:';

// 寻踪开关进入服务端回执载荷，开与关必须用不同的待确认键，否则切换后重试会撞上回执冲突。
function storageKey(playerId: number, locationKey: string, traceRoute = false): string {
  return `${STORAGE_PREFIX}${playerId}:${encodeURIComponent(locationKey)}${traceRoute ? ':trace' : ''}`;
}

function createRequestKey(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `explore_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
}

/** Reuse an unconfirmed action key only for the same player and scene. */
export function getPendingExploreRequestKey(playerId: number, locationKey: string, traceRoute = false): string {
  const key = storageKey(playerId, locationKey, traceRoute);
  try {
    const existing = globalThis.sessionStorage.getItem(key);
    if (existing && existing.length <= 64) return existing;

    const next = createRequestKey();
    globalThis.sessionStorage.setItem(key, next);
    pendingKeys.set(key, next);
    return next;
  } catch {
    const existing = pendingKeys.get(key);
    if (existing) return existing;
    const next = createRequestKey();
    pendingKeys.set(key, next);
    return next;
  }
}

/** Clear only the key acknowledged by the server; never erase a newer request. */
export function clearPendingExploreRequestKey(
  playerId: number,
  locationKey: string,
  requestKey: string,
  traceRoute = false,
): void {
  const key = storageKey(playerId, locationKey, traceRoute);
  if (pendingKeys.get(key) === requestKey) pendingKeys.delete(key);
  try {
    if (globalThis.sessionStorage.getItem(key) === requestKey) {
      globalThis.sessionStorage.removeItem(key);
    }
  } catch {
    // Storage can be disabled by the browser; the in-memory key is still cleared above.
  }
}
