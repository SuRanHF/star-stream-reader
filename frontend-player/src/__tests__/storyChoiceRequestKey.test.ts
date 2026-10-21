import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearPendingStoryChoiceRequest,
  getOrCreatePendingStoryChoiceRequest,
  getPendingStoryChoiceRequest,
} from '@/utils/storyChoiceRequestKey';

describe('storyChoiceRequestKey', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it('同一剧情实例的待确认选择跨组件重建后仍复用请求键和选项', () => {
    const first = getOrCreatePendingStoryChoiceRequest(7, 'forked-road', 'instance-1', 1);

    expect(getPendingStoryChoiceRequest(7, 'forked-road', 'instance-1')).toEqual(first);
    expect(getOrCreatePendingStoryChoiceRequest(7, 'forked-road', 'instance-1', 0)).toEqual(first);
  });

  it('同一可重复剧情的新实例不会误用上一次选择回执', () => {
    const first = getOrCreatePendingStoryChoiceRequest(7, 'repeatable-story', 'instance-1', 0);
    clearPendingStoryChoiceRequest(7, 'repeatable-story', 'instance-1', first.requestKey);
    const next = getOrCreatePendingStoryChoiceRequest(7, 'repeatable-story', 'instance-2', 0);

    expect(next.requestKey).not.toBe(first.requestKey);
    expect(getPendingStoryChoiceRequest(7, 'repeatable-story', 'instance-1')).toBeNull();
  });

  it('只清除服务端确认的那个请求键', () => {
    const pending = getOrCreatePendingStoryChoiceRequest(7, 'forked-road', 'instance-1', 0);

    clearPendingStoryChoiceRequest(7, 'forked-road', 'instance-1', 'different-request');
    expect(getPendingStoryChoiceRequest(7, 'forked-road', 'instance-1')).toEqual(pending);

    clearPendingStoryChoiceRequest(7, 'forked-road', 'instance-1', pending.requestKey);
    expect(getPendingStoryChoiceRequest(7, 'forked-road', 'instance-1')).toBeNull();
  });
});
