package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.entity.Player;

/**
 * 玩家角色服务接口.
 */
public interface PlayerService {

    /**
     * 创建玩家角色 (每个用户只能创建一个).
     *
     * @param playerName 角色名称
     * @param userId     所属用户 ID
     * @return 创建后的 PlayerVO
     */
    PlayerVO create(String playerName, Long userId);

    /**
     * 根据玩家 ID 获取玩家 (含解析后的 JSON 字段 + 最近日志).
     *
     * @param playerId 玩家 ID
     * @return PlayerVO, 不存在时返回 null
     */
    PlayerVO getPlayer(Long playerId);

    /**
     * 根据玩家 ID 获取原始实体 (Service 内部使用).
     *
     * @param playerId 玩家 ID
     * @return Player 实体, 不存在时返回 null
     */
    Player getPlayerEntity(Long playerId);

    /**
     * 根据用户 ID 查找玩家.
     *
     * @param userId 用户 ID
     * @return Player 实体, 不存在时返回 null
     */
    Player findByUserId(Long userId);
}
