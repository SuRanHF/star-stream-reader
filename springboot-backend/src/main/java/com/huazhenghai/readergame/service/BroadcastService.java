package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.BroadcastEventVO;
import com.huazhenghai.readergame.vo.BroadcastHistoryVO;
import com.huazhenghai.readergame.vo.BroadcastSummaryVO;

import java.util.List;
import java.util.Map;

public interface BroadcastService {

    List<BroadcastEventVO> getActiveBroadcasts();

    /** 最近已达成或已到期的公共活动；只返回公开结局摘要，不含个人奖励或贡献明细。 */
    List<BroadcastHistoryVO> getHistory(int limit);

    BroadcastEventVO getBroadcastDetail(String eventKey);

    Map<String, Object> contribute(String eventKey, Long playerId, int value, String contributionType);

    /** Apply one completed gameplay action to matching objectives on active AI broadcasts. */
    int contributeToActiveAiEvents(Long playerId, String objectiveType, int units);

    Map<String, Object> claimReward(String eventKey, Long playerId);

    List<Map<String, Object>> getPlayerContributions(Long playerId);

    /** 获取指定放送活动的全服贡献排行；eventKey 为空时保留旧版全活动汇总兼容行为。 */
    List<Map<String, Object>> getLeaderboard(String eventKey, int limit);

    /** 获取玩家在指定放送活动中的名次、贡献和追赶上一名所需分数。 */
    Map<String, Object> getPlayerLeaderboardPosition(String eventKey, Long playerId);

    BroadcastSummaryVO getBroadcastSummary();

    /** 从 AI Director 草稿创建广播事件，返回 eventKey，失败返回 null */
    String createEvent(Map<String, Object> draft);
}
