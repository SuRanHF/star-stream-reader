package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.BroadcastEventVO;
import com.huazhenghai.readergame.vo.BroadcastSummaryVO;

import java.util.List;
import java.util.Map;

public interface BroadcastService {

    List<BroadcastEventVO> getActiveBroadcasts();

    BroadcastEventVO getBroadcastDetail(String eventKey);

    Map<String, Object> contribute(String eventKey, Long playerId, int value, String contributionType);

    Map<String, Object> claimReward(String eventKey, Long playerId);

    List<Map<String, Object>> getPlayerContributions(Long playerId);

    BroadcastSummaryVO getBroadcastSummary();

    /** 从 AI Director 草稿创建广播事件，返回 eventKey，失败返回 null */
    String createEvent(Map<String, Object> draft);
}
