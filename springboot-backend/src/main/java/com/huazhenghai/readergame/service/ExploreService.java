package com.huazhenghai.readergame.service;

import java.util.List;
import java.util.Map;

public interface ExploreService {

    /**
     * 执行一次探索.
     *
     * @param playerId    玩家 ID
     * @param locationKey 地点 key (为空则用玩家当前地点)
     * @param userId      当前登录用户 ID (所有权校验)
     * @return 探索结果 Map (含 resultType, event, choices, player, newLogs, storiesExhausted 等)
     */
    Map<String, Object> startExplore(Long playerId, String locationKey, Long userId);

    /**
     * 在故事事件中做出选择.
     *
     * @param playerId    玩家 ID
     * @param eventKey    事件 key
     * @param choiceIndex 选项索引 (0-based)
     * @param userId      当前登录用户 ID
     * @return 选择后果 Map (含 consequence, rewards, unlockLocations, unlockEvents, titleBias 等)
     */
    Map<String, Object> makeChoice(Long playerId, String eventKey, int choiceIndex, Long userId);

    /**
     * 获取玩家的故事回顾日志.
     *
     * @param playerId 玩家 ID
     * @return 故事日志列表
     */
    List<Map<String, Object>> getStoryLog(Long playerId);
}
