package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.AvatarRankVO;

import java.util.List;
import java.util.Map;

/**
 * 化身位阶系统服务接口.
 */
public interface AvatarRankService {

    /**
     * 获取玩家位阶详细信息 (含升阶进度).
     */
    AvatarRankVO getAvatarRankInfo(Long playerId);

    /**
     * 尝试升阶.
     */
    AvatarRankVO rankUp(Long playerId, Long userId);

    /**
     * 根据频道热度计算星流层级.
     */
    Map<String, Object> getStarstreamTier(int channelHeat);

    /**
     * 获取位阶排行榜 (按 orderNum 降序).
     */
    List<Map<String, Object>> getAvatarRankLeaderboard();
}
