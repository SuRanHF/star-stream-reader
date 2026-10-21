export interface PendingStoryChoiceRequest {
  requestKey: string;
  choiceIndex: number;
}

const pendingRequests = new Map<string, PendingStoryChoiceRequest>();
const STORAGE_PREFIX = 'reader-game:pending-story-choice:v1:';

function storageKey(playerId: number, eventKey: string, storyInstanceKey?: string): string {
  const instance = storyInstanceKey || `legacy:${eventKey}`;
  return `${STORAGE_PREFIX}${playerId}:${encodeURIComponent(eventKey)}:${encodeURIComponent(instance)}`;
}

function createRequestKey(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `story_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
}

function parsePendingRequest(value: string | null): PendingStoryChoiceRequest | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value) as Partial<PendingStoryChoiceRequest>;
    if (typeof parsed.requestKey === 'string'
        && parsed.requestKey.length > 0
        && parsed.requestKey.length <= 64
        && Number.isInteger(parsed.choiceIndex)
        && Number(parsed.choiceIndex) >= 0) {
      return { requestKey: parsed.requestKey, choiceIndex: Number(parsed.choiceIndex) };
    }
  } catch {
    // Ignore stale or malformed browser storage and create a fresh request below.
  }
  return null;
}

/** Read the unresolved selection for this exact pending story instance. */
export function getPendingStoryChoiceRequest(
  playerId: number,
  eventKey: string,
  storyInstanceKey?: string,
): PendingStoryChoiceRequest | null {
  const key = storageKey(playerId, eventKey, storyInstanceKey);
  try {
    const pending = parsePendingRequest(globalThis.sessionStorage.getItem(key));
    if (pending) {
      pendingRequests.set(key, pending);
      return pending;
    }
    pendingRequests.delete(key);
    return null;
  } catch {
    return pendingRequests.get(key) || null;
  }
}

/** Save the first submitted choice so retries, including after a refresh, replay it. */
export function getOrCreatePendingStoryChoiceRequest(
  playerId: number,
  eventKey: string,
  storyInstanceKey: string | undefined,
  choiceIndex: number,
): PendingStoryChoiceRequest {
  const existing = getPendingStoryChoiceRequest(playerId, eventKey, storyInstanceKey);
  if (existing) return existing;

  const key = storageKey(playerId, eventKey, storyInstanceKey);
  const pending = { requestKey: createRequestKey(), choiceIndex };
  pendingRequests.set(key, pending);
  try {
    globalThis.sessionStorage.setItem(key, JSON.stringify(pending));
  } catch {
    // Keep the key in memory when browser storage is unavailable.
  }
  return pending;
}

/** Clear only the receipt that the server acknowledged. */
export function clearPendingStoryChoiceRequest(
  playerId: number,
  eventKey: string,
  storyInstanceKey: string | undefined,
  requestKey: string,
): void {
  const key = storageKey(playerId, eventKey, storyInstanceKey);
  const pending = getPendingStoryChoiceRequest(playerId, eventKey, storyInstanceKey);
  if (pending?.requestKey !== requestKey) return;

  pendingRequests.delete(key);
  try {
    globalThis.sessionStorage.removeItem(key);
  } catch {
    // Storage can be disabled; memory state has still been cleared.
  }
}
