package com.huazhenghai.readergame.service;

import java.util.List;
import java.util.Map;

public interface ExploreService {

    /**
     * 执行一次探索.
     *
     * @param playerId    玩家 ID
     * @param locationKey 地点 key (为空则用玩家当前地点)
     * @param requestKey  本次探索的幂等请求键
     * @param userId      当前登录用户 ID (所有权校验)
     * @return 探索结果 Map (含 resultType, event, choices, player, newLogs, storiesExhausted 等)
     */
    Map<String, Object> startExplore(Long playerId, String locationKey, String requestKey, Long userId);

    /**
     * 在故事事件中做出选择.
     *
     * @param playerId    玩家 ID
     * @param eventKey    事件 key
     * @param choiceIndex 选项索引 (0-based)
     * @param userId      当前登录用户 ID
     * @return 选择后果 Map (含 consequence, rewards, unlockLocations, unlockEvents, titleBias 等)
     */
    /** 使用请求键保证故事选择的奖励与后果不会因网络重试重复结算。 */
    Map<String, Object> makeChoice(Long playerId, String eventKey, int choiceIndex,
                                   String requestKey, Long userId);

    /** 额外绑定待选剧情实例，避免旧回执被误用于同一可重复剧情的下一次触发。 */
    Map<String, Object> makeChoice(Long playerId, String eventKey, int choiceIndex,
                                   String requestKey, String storyInstanceKey, Long userId);

    /** 获取已触发但尚未选择的一次性剧情，供玩家稍后恢复。 */
    List<Map<String, Object>> getPendingStories(Long playerId, Long userId);

    /** 获取已触发但尚未完成战斗的遭遇，供玩家稍后恢复。 */
    List<Map<String, Object>> getPendingBattles(Long playerId, Long userId);

    /** 确认战斗遭遇已完成，避免后续启动时再次恢复。 */
    void acknowledgePendingBattle(Long playerId, String requestKey, Long userId);

    /**
     * 获取玩家的故事回顾日志.
     *
     * @param playerId 玩家 ID
     * @return 故事日志列表
     */
    List<Map<String, Object>> getStoryLog(Long playerId);
}
