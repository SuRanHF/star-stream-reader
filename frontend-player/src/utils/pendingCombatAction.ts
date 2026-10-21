export type CombatAction = 'fight' | 'flee';

const STORAGE_PREFIX = 'reader-game:pending-combat-action:v1:';
const inMemoryActions = new Map<string, CombatAction>();

function storageKey(playerId: number, requestKey: string): string {
  if (!Number.isSafeInteger(playerId) || playerId <= 0 || !requestKey || requestKey.length > 64) {
    return '';
  }
  return `${STORAGE_PREFIX}${playerId}:${encodeURIComponent(requestKey)}`;
}

/** Remember which action may already be settling so a reload cannot submit its opposite. */
export function setPendingCombatAction(
  playerId: number,
  requestKey: string,
  action: CombatAction,
): void {
  const key = storageKey(playerId, requestKey);
  if (!key) return;
  inMemoryActions.set(key, action);
  try {
    globalThis.localStorage.setItem(key, action);
  } catch {
    // Keep the same-tab fallback when browser storage is unavailable.
  }
}

export function getPendingCombatAction(
  playerId: number,
  requestKey: string,
): CombatAction | null {
  const key = storageKey(playerId, requestKey);
  if (!key) return null;

  try {
    const stored = globalThis.localStorage.getItem(key);
    if (stored === 'fight' || stored === 'flee') {
      inMemoryActions.set(key, stored);
      return stored;
    }
    if (stored !== null) globalThis.localStorage.removeItem(key);
  } catch {
    // Fall through to the same-tab fallback.
  }
  return inMemoryActions.get(key) ?? null;
}

export function clearPendingCombatAction(playerId: number, requestKey: string): void {
  const key = storageKey(playerId, requestKey);
  if (!key) return;
  inMemoryActions.delete(key);
  try {
    globalThis.localStorage.removeItem(key);
  } catch {
    // The in-memory copy is already cleared.
  }
}
