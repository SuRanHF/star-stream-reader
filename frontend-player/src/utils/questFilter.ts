import type { ApiRecord } from '@/types/api';

const QUEST_FILTER_BY_TAB: Record<string, { fields: readonly string[]; types: readonly string[] }> = {
  '主线任务': { fields: ['questType', 'quest_type', 'category'], types: ['main', 'story', '主线'] },
  '每日委托': { fields: ['questType', 'quest_type', 'cycleKey'], types: ['daily', '每日'] },
  '每周委托': { fields: ['questType', 'quest_type', 'cycleKey'], types: ['weekly', '每周'] },
  '世界线成就': { fields: ['questType', 'quest_type', 'category'], types: ['achievement', '成就'] },
};

export function filterQuestItems(quests: ApiRecord[], tab: string): ApiRecord[] {
  const filter = QUEST_FILTER_BY_TAB[tab];
  if (!filter) return quests;

  return quests.filter((quest) => {
    const type = filter.fields
      .map((field) => quest[field])
      .find((value) => value !== undefined && value !== null && value !== '');
    return filter.types.includes(String(type ?? '').toLowerCase());
  });
}
