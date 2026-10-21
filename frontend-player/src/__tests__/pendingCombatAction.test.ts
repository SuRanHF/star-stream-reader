import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearPendingCombatAction,
  getPendingCombatAction,
  setPendingCombatAction,
} from '@/utils/pendingCombatAction';

describe('pendingCombatAction', () => {
  beforeEach(() => localStorage.clear());

  it('stores the uncertain action per player and request key for reload recovery', () => {
    setPendingCombatAction(7, 'explore-receipt-42', 'fight');

    expect(getPendingCombatAction(7, 'explore-receipt-42')).toBe('fight');
    expect(getPendingCombatAction(8, 'explore-receipt-42')).toBeNull();
    expect(getPendingCombatAction(7, 'another-receipt')).toBeNull();
  });

  it('clears the action only after the encounter is confirmed or rejected', () => {
    setPendingCombatAction(7, 'explore-receipt-42', 'flee');
    clearPendingCombatAction(7, 'explore-receipt-42');

    expect(getPendingCombatAction(7, 'explore-receipt-42')).toBeNull();
  });

  it('ignores invalid player ids and oversized request keys', () => {
    setPendingCombatAction(0, 'request', 'fight');
    setPendingCombatAction(7, 'x'.repeat(65), 'flee');

    expect(localStorage.length).toBe(0);
    expect(getPendingCombatAction(0, 'request')).toBeNull();
    expect(getPendingCombatAction(7, 'x'.repeat(65))).toBeNull();
  });
});
