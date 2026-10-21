import type { ApiRecord } from '@/types/api';

const QUEST_DEPENDENCY_HINTS: Record<string, string> = {
  daily_trade_1: '需要买入其他玩家的挂单；不能购买自己的挂单。',
  daily_trade_sell_1: '需要上架物品或装备，并由其他玩家完成购买。',
  weekly_pk_5: '需要与在线玩家完成 5 次对战；对方接受挑战后才计数。',
  weekly_pk_win_3: '需要赢得 3 场与在线玩家的对战；对方需接受挑战。',
  weekly_boss_3: '仅首领开放时可攻击；每次攻击有 30 秒冷却，进度按攻击次数计算。',
};

export function questDependencyHint(quest: ApiRecord): string {
  const key = quest.questKey ?? quest.quest_key ?? quest.key;
  return QUEST_DEPENDENCY_HINTS[String(key ?? '').trim().toLowerCase()] ?? '';
}
