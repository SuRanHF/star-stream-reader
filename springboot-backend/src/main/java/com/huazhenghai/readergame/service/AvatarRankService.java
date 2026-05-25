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

    /**
     * 根据位阶获取经验倍率 (阶梯型: 前面低, 后面高).
     */
    default double getExpMultiplier(String rankKey) {
        if (rankKey == null) return 1.0;
        return switch (rankKey.toUpperCase()) {
            case "E" -> 1.5;
            case "D" -> 2.5;
            case "C" -> 4.0;
            case "B" -> 7.0;
            case "A" -> 12.0;
            case "S" -> 20.0;
            case "SS" -> 35.0;
            case "SSS" -> 60.0;
            default -> 1.0; // F or unknown
        };
    }
}
