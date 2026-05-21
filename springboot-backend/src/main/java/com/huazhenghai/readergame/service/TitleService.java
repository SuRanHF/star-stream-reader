package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.TitleVO;

import java.util.List;
import java.util.Map;

/**
 * 称号系统服务接口.
 */
public interface TitleService {

    /**
     * 获取所有已启用的称号定义.
     */
    List<TitleVO> getAllTitles();

    /**
     * 获取玩家的所有称号.
     */
    List<PlayerTitleVO> getPlayerTitles(Long playerId);

    /**
     * 获取玩家当前装备的称号.
     */
    PlayerTitleVO getEquippedTitle(Long playerId);

    /**
     * 评估并解锁新称号 (探索后调用).
     * @param playerId 玩家ID
     * @param userId 操作用户ID (null表示内部调用，不做权限校验)
     * @return 本次新解锁的称号列表
     */
    List<PlayerTitleVO> evaluateAndUnlockTitles(Long playerId, Long userId);

    /**
     * 装备称号.
     */
    PlayerTitleVO equipTitle(Long playerId, String titleKey, Long userId);

    /**
     * 计算当前装备称号的效果加成.
     */
    Map<String, Object> calculateTitleEffects(Long playerId);

    /**
     * 直接授予玩家指定称号 (任务奖励等内部调用).
     */
    void grantTitle(Long playerId, String titleKey);
}
