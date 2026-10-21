import { describe, expect, it } from 'vitest';
import { buildEndingArchiveEntries, hasEndingArchiveLoadError } from '@/utils/endingArchive';

describe('personal ending archive', () => {
  it('shows only the achieved endings returned for this character', () => {
    const entries = buildEndingArchiveEntries([
      {
        endingKey: 'end_continue_dream',
        name: '接续之梦：新的最古老之梦',
        description: '你成为新的最古老之梦。',
      },
      {
        ending_key: 'end_frozen_world',
        ending_name: '停滞的世界线（假设分支）',
        description: '世界时间停止。',
        is_hidden: 1,
      },
    ]);

    expect(entries).toEqual([
      {
        key: 'end_continue_dream',
        name: '接续之梦：新的最古老之梦',
        description: '你成为新的最古老之梦。',
        isHidden: false,
      },
      {
        key: 'end_frozen_world',
        name: '停滞的世界线（假设分支）',
        description: '世界时间停止。',
        isHidden: true,
      },
    ]);
  });

  it('does not turn an endpoint error placeholder into a fake ending', () => {
    const unavailable = [{ status: '接口错误', message: 'timeout' }];

    expect(buildEndingArchiveEntries(unavailable)).toEqual([]);
    expect(hasEndingArchiveLoadError(unavailable)).toBe(true);
    expect(hasEndingArchiveLoadError([])).toBe(false);
  });
});
