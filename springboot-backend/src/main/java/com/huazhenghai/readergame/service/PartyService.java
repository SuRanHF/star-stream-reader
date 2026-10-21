package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PartySummaryVO;
import com.huazhenghai.readergame.vo.PartyVO;

import java.util.List;
import java.util.Map;

public interface PartyService {

    PartyVO createParty(Long playerId, String name, String description, Long userId);

    PartyVO joinParty(Long playerId, String partyNo, Long userId);

    PartyVO leaveParty(Long playerId, Long userId);

    PartyVO kickMember(Long leaderPlayerId, Long targetPlayerId, Long userId);

    PartyVO transferLeader(Long leaderPlayerId, Long targetPlayerId, Long userId);

    void disbandParty(Long leaderPlayerId, Long userId);

    PartyVO getMyParty(Long playerId, Long userId);

    PartyVO getPartyByNo(String partyNo);

    PartySummaryVO getPartySummary(Long playerId);

    List<PartyVO> getActiveParties();

    /** 设置当前队伍成员的准备状态。 */
    PartyVO setReady(Long playerId, boolean ready, Long userId);

    /**
     * 发起一次队伍世界 Boss 讨伐。队伍校验在组队服务完成，伤害结算委托给世界 Boss 服务。
     */
    Map<String, Object> startPartyBossBattle(String partyNo, Long playerId, Long userId, String requestKey);
}
