package com.huazhenghai.readergame.service;

import java.util.Map;

public interface ExploreService {

    /**
     * 执行一次探索.
     *
     * @param playerId    玩家 ID
     * @param locationKey 地点 key (为空则用玩家当前地点)
     * @param userId      当前登录用户 ID (所有权校验)
     * @return 探索结果 Map (含 resultType, event, player, newLogs 等)
     */
    Map<String, Object> startExplore(Long playerId, String locationKey, Long userId);
}
