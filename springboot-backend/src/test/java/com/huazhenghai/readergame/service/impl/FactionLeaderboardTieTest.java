package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.ConstellationFaction;
import com.huazhenghai.readergame.entity.PlayerFaction;
import com.huazhenghai.readergame.mapper.ConstellationFactionMapper;
import com.huazhenghai.readergame.mapper.FactionContributionMapper;
import com.huazhenghai.readergame.mapper.FactionSkillMapper;
import com.huazhenghai.readergame.mapper.FactionWarMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionSkillMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.vo.FactionRankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactionLeaderboardTieTest {
    @Mock private ConstellationFactionMapper factionMapper;
    @Mock private PlayerFactionMapper playerFactionMapper;
    @Mock private FactionContributionMapper contributionMapper;
    @Mock private FactionWarMapper warMapper;
    @Mock private FactionSkillMapper skillMapper;
    @Mock private PlayerFactionSkillMapper playerSkillMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private QuestService questService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private FactionServiceImpl factionService;

    @BeforeEach
    void setUp() {
        factionService = new FactionServiceImpl(factionMapper, playerFactionMapper,
                contributionMapper, warMapper, skillMapper, playerSkillMapper,
                playerMapper, playerLogMapper, new ObjectMapper(), questService,
                actionReceiptService, resourceTransactionService);
    }

    @Test
    void factionTotalsShareCompetitionRanksAndUseFactionKeyAsStableTieBreaker() {
        List<ConstellationFaction> factions = List.of(
                faction("alpha", 120), faction("beta", 120),
                faction("gamma", 80), faction("delta", 80));
        AtomicReference<String> sql = new AtomicReference<>();
        when(factionMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<ConstellationFaction> query = invocation.getArgument(0);
            sql.set(query.getSqlSegment().toLowerCase(Locale.ROOT));
            return factions;
        });

        List<FactionRankingVO> rankings = factionService.getFactionRankings();

        assertEquals(List.of(1, 1, 3, 3), rankings.stream().map(FactionRankingVO::getRank).toList());
        assertEquals(List.of("alpha", "beta", "gamma", "delta"),
                rankings.stream().map(FactionRankingVO::getFactionKey).toList());
        assertTrue(sql.get().contains("total_contribution desc"));
        assertTrue(sql.get().contains("faction_key asc"));
    }

    @Test
    void memberContributionsShareCompetitionRanksAndUsePlayerIdAsStableTieBreaker() {
        List<PlayerFaction> members = List.of(
                member(11L, 90), member(22L, 90), member(33L, 35));
        AtomicReference<String> sql = new AtomicReference<>();
        when(playerFactionMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<PlayerFaction> query = invocation.getArgument(0);
            sql.set(query.getSqlSegment().toLowerCase(Locale.ROOT));
            return members;
        });

        List<FactionRankingVO> rankings = factionService.getFactionContributionRankings("alpha");

        assertEquals(List.of(1, 1, 3), rankings.stream().map(FactionRankingVO::getRank).toList());
        assertEquals(List.of(11L, 22L, 33L), rankings.stream().map(FactionRankingVO::getPlayerId).toList());
        assertTrue(sql.get().contains("contribution_total desc"));
        assertTrue(sql.get().contains("player_id asc"));
        assertTrue(sql.get().contains("limit 50"));
    }

    private ConstellationFaction faction(String key, long contribution) {
        ConstellationFaction faction = new ConstellationFaction();
        faction.setFactionKey(key);
        faction.setName(key);
        faction.setTotalContribution(contribution);
        return faction;
    }

    private PlayerFaction member(Long playerId, long contribution) {
        PlayerFaction member = new PlayerFaction();
        member.setPlayerId(playerId);
        member.setFactionKey("alpha");
        member.setStatus("active");
        member.setContributionTotal(contribution);
        return member;
    }
}
