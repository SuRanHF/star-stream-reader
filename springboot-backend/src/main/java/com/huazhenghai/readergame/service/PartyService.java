package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PartySummaryVO;
import com.huazhenghai.readergame.vo.PartyVO;

import java.util.List;

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
}
