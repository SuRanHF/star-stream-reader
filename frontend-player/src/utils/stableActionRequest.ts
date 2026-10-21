const STORAGE_PREFIX = 'reader-game:action-request:v1:';
const inMemoryKeys = new Map<string, string>();

function createRequestKey(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  const bytes = new Uint8Array(16);
  if (typeof globalThis.crypto?.getRandomValues === 'function') globalThis.crypto.getRandomValues(bytes);
  else bytes.forEach((_, index) => { bytes[index] = Math.floor(Math.random() * 256); });
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function storageKey(scope: string): string {
  return `${STORAGE_PREFIX}${encodeURIComponent(scope)}`;
}

function getOrCreateRequestKey(key: string): string {
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

function clearRequestKey(key: string, requestKey: string): void {
  if (inMemoryKeys.get(key) === requestKey) inMemoryKeys.delete(key);
  try {
    if (globalThis.localStorage.getItem(key) === requestKey) globalThis.localStorage.removeItem(key);
  } catch {
    // The in-memory key was cleared above; browser storage may be disabled.
  }
}

/**
 * Reuse a request key after an uncertain network result, and clear it only
 * after the server returns a successful response.
 */
export async function withStableActionRequest<T>(
  scope: string,
  send: (requestKey: string) => Promise<T>,
): Promise<T> {
  const key = storageKey(scope);
  const requestKey = getOrCreateRequestKey(key);
  const result = await send(requestKey);
  clearRequestKey(key, requestKey);
  return result;
}
