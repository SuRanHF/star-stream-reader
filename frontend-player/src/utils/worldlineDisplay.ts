export const WORLDLINE_HISTORY_DESCRIPTION =
  '这里记录全服活动与世界首领结算带来的变化。自然回落会影响当前数值，但不逐次占用历史记录。';

export function worldlineCauseDisplay(entry: { cause?: unknown; causeDisplay?: unknown }): string {
  if (typeof entry.causeDisplay === 'string' && entry.causeDisplay.trim()) {
    return entry.causeDisplay;
  }

  const cause = typeof entry.cause === 'string' ? entry.cause : '';
  if (cause.startsWith('world_boss_expired_')) return '世界首领未能及时击破';
  if (cause.startsWith('world_boss_')) return '世界首领被击破';
  if (cause) return '全服活动结算的公共影响';
  return '公共世界事件影响';
}
