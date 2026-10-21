import { beforeEach, describe, expect, it } from 'vitest';
import { withStableActionRequest } from '@/utils/stableActionRequest';

describe('stableActionRequest', () => {
  beforeEach(() => localStorage.clear());

  it('对不确定的失败重试复用请求键，成功后才清除', async () => {
    const keys: string[] = [];
    const send = async (requestKey: string) => {
      keys.push(requestKey);
      if (keys.length === 1) throw new Error('network timeout');
      return 'ok';
    };

    await expect(withStableActionRequest('trade:buy:1:TL-1', send)).rejects.toThrow('network timeout');
    expect(localStorage.length).toBe(1);
    await expect(withStableActionRequest('trade:buy:1:TL-1', send)).resolves.toBe('ok');

    expect(keys[1]).toBe(keys[0]);
    expect(localStorage.length).toBe(0);
  });

  it('不同操作目标使用互相隔离的请求键', async () => {
    const keys: string[] = [];
    await withStableActionRequest('trade:buy:1:TL-1', async (key) => { keys.push(key); return true; });
    await withStableActionRequest('trade:buy:1:TL-2', async (key) => { keys.push(key); return true; });

    expect(keys[0]).not.toBe(keys[1]);
  });
});
