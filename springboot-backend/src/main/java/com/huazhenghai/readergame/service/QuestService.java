package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;

public interface QuestService {
    /** 获取玩家任务列表 (含自动初始化) */
    List<PlayerQuestVO> getAvailableQuests(Long playerId, Long userId);

    /** 确保当前周期任务已初始化 */
    void ensurePlayerQuests(Long playerId);

    /** 自动推进任务进度 (内部使用，不暴露给前端) */
    List<PlayerQuestVO> addProgress(Long playerId, String targetType, int delta, String source, String relatedId);

    /** 领取任务奖励 */
    QuestRewardVO claimReward(Long playerId, String questKey, String cycleKey, Long userId);

    /** 过期旧日常任务 (Scheduler) */
    int refreshDailyQuests();

    /** 过期旧周常任务 (Scheduler) */
    int refreshWeeklyQuests();

    /** 获取任务摘要 (bootstrap) */
    QuestSummaryVO getQuestSummary(Long playerId);

    /** 评估成长类任务 (等级/位阶) */
    void evaluateGrowthQuests(Long playerId);

    /** 获取所有任务定义 */
    List<QuestVO> getQuestDefinitions();
}
