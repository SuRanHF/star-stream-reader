package com.huazhenghai.readergame.service;

import java.util.List;
import java.util.Map;

public interface HelpBountyService {

    Map<String, Object> getDailyCounts(Long playerId);

    Map<String, Object> publishBounty(Long playerId, String monsterKey, String locationKey, String monsterName, int sharePercent, Map<String, Object> combatRewards);

    Map<String, Object> acceptBounty(Long bountyId, Long helperId);

    List<Map<String, Object>> getPendingBounties();

    Map<String, Object> getMyActiveBounty(Long playerId);

    Map<String, Object> cancelBounty(Long playerId);
}
