import { describe, expect, it } from 'vitest';
import { queryKeys } from '@/api/queryKeys';

describe('game bootstrap query key', () => {
  it('keeps each authenticated player in a separate cache entry', () => {
    expect(queryKeys.gameBootstrap(101)).not.toEqual(queryKeys.gameBootstrap(202));
  });

  it('uses a stable fallback before a stored user identity is available', () => {
    expect(queryKeys.gameBootstrap()).toEqual(['game-bootstrap', 'unknown-user']);
    expect(queryKeys.gameBootstrap(null)).toEqual(['game-bootstrap', 'unknown-user']);
  });
});
