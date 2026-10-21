const KNOWN_STORY_FLAG_EFFECTS: Record<string, string> = {
  story_dark_spring_interwoven: '个人分支：并置两段记录',
  story_dark_spring_separated: '个人分支：分别保存两段记录',
  ending_old_dream_ended: '终局进度：已履行终结旧梦的约定',
  ending_dream_continued: '终局结果：接续梦境',
  ending_new_oldest_dream: '终局结果：成为新的最古老之梦',
  ending_world_frozen: '终局结果：世界时间停滞（假设分支）',
};

/** Convert persisted, server-applied personal flags into player-readable outcomes. */
export function describeStoryFlagEffects(value: unknown): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return [];

  const effects = new Set<string>();
  for (const [flag, enabled] of Object.entries(value)) {
    if (enabled !== true) continue;
    const knownEffect = KNOWN_STORY_FLAG_EFFECTS[flag];
    if (knownEffect) {
      effects.add(knownEffect);
    } else if (flag.startsWith('ending_')) {
      effects.add('更新个人终局进度');
    } else if (flag.startsWith('story_')) {
      effects.add('记录个人故事分支');
    }
  }
  return [...effects];
}
