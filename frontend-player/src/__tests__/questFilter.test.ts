import { describe, expect, it } from 'vitest';
import { filterQuestItems } from '@/utils/questFilter';

const quests = [
  { questKey: 'daily_explore_3', questType: 'daily' },
  { questKey: 'weekly_pk_5', quest_type: 'weekly' },
  { questKey: 'main_story', category: 'story' },
  { questKey: 'achv_level_5', questType: 'achievement' },
];

describe('filterQuestItems', () => {
  it('exposes weekly tasks only under the weekly commission tab', () => {
    expect(filterQuestItems(quests, '每周委托').map((quest) => quest.questKey))
      .toEqual(['weekly_pk_5']);
    expect(filterQuestItems(quests, '每日委托').map((quest) => quest.questKey))
      .toEqual(['daily_explore_3']);
  });

  it('keeps existing story, achievement, and catch-all tab behavior', () => {
    expect(filterQuestItems(quests, '主线任务').map((quest) => quest.questKey))
      .toEqual(['main_story']);
    expect(filterQuestItems(quests, '世界线成就').map((quest) => quest.questKey))
      .toEqual(['achv_level_5']);
    expect(filterQuestItems(quests, '快速处理')).toBe(quests);
  });
});
