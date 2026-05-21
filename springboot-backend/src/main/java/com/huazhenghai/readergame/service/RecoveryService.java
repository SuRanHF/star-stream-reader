package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.entity.Player;

import java.util.Map;

public interface RecoveryService {

    /**
     * 被动恢复: 根据距离上次恢复的时间计算并更新 HP/体力.
     */
    Map<String, Object> applyRecovery(Player player);

    /**
     * 开始休息.
     */
    Map<String, Object> startRest(Long playerId, Long userId);

    /**
     * 结束休息.
     */
    Map<String, Object> stopRest(Long playerId, Long userId);

    /**
     * 检查玩家是否可以行动 (不处于休息状态).
     */
    void assertCanAct(Player player);
}
