package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.FactionContributionVO;
import com.huazhenghai.readergame.vo.FactionSkillVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactionServiceImplResourceLockTest {
    private static final Long PLAYER_ID = 17L;
    private static final Long USER_ID = 29L;
    private static final String FACTION_KEY = "nebula_abyss";
    private static final String SKILL_KEY = "abyss_gaze";

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
    void learningFactionSkillLocksBothBalancesAndPersistsTheSpend() throws NoSuchMethodException {
        Player player = player(100);
        PlayerFaction membership = membership(100);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerFactionMapper.selectActiveByPlayerIdForUpdate(PLAYER_ID)).thenReturn(membership);
        when(skillMapper.selectOne(any())).thenReturn(skill(40, 25));
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);
        when(factionMapper.selectOne(any())).thenReturn(faction());
        when(playerSkillMapper.insert(any(PlayerFactionSkill.class))).thenReturn(1);
        when(playerFactionMapper.updateById(membership)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);

        FactionSkillVO result = factionService.learnFactionSkill(PLAYER_ID, SKILL_KEY, USER_ID, "unit-faction-skill");

        assertTrue(result.isLearned());
        assertEquals(60, player.getCoins());
        assertEquals(75L, membership.getReputation());
        InOrder locks = inOrder(playerMapper, playerFactionMapper);
        locks.verify(playerMapper).selectByIdForUpdate(PLAYER_ID);
        locks.verify(playerFactionMapper).selectActiveByPlayerIdForUpdate(PLAYER_ID);
        verify(playerSkillMapper).insert(any(PlayerFactionSkill.class));
        verify(playerLogMapper).insert(any(PlayerLog.class));
        verify(resourceTransactionService, times(2)).record(any(PlayerResourceTransactionService.Change.class));
        verify(actionReceiptService).record(eq(PLAYER_ID), eq("faction.skill-learn"),
                eq("unit-faction-skill"), any(), any(FactionSkillVO.class));
        assertNotNull(FactionServiceImpl.class
                .getMethod("learnFactionSkill", Long.class, String.class, Long.class, String.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void insufficientCoinsDoesNotWriteOrSpendAnyResource() {
        Player player = player(39);
        PlayerFaction membership = membership(100);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerFactionMapper.selectActiveByPlayerIdForUpdate(PLAYER_ID)).thenReturn(membership);
        when(skillMapper.selectOne(any())).thenReturn(skill(40, 25));
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);
        when(factionMapper.selectOne(any())).thenReturn(faction());

        assertThrows(BusinessException.class,
                () -> factionService.learnFactionSkill(PLAYER_ID, SKILL_KEY, USER_ID, "unit-faction-poor"));

        assertEquals(39, player.getCoins());
        assertEquals(100L, membership.getReputation());
        verify(playerSkillMapper, never()).insert(any(PlayerFactionSkill.class));
        verify(playerFactionMapper, never()).updateById(any(PlayerFaction.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
        verify(resourceTransactionService, never()).record(any());
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void failedSkillInsertDoesNotReportSuccessOrWriteLog() {
        Player player = player(100);
        PlayerFaction membership = membership(100);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerFactionMapper.selectActiveByPlayerIdForUpdate(PLAYER_ID)).thenReturn(membership);
        when(skillMapper.selectOne(any())).thenReturn(skill(40, 25));
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);
        when(factionMapper.selectOne(any())).thenReturn(faction());
        when(playerSkillMapper.insert(any(PlayerFactionSkill.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> factionService.learnFactionSkill(PLAYER_ID, SKILL_KEY, USER_ID, "unit-faction-insert-failure"));

        assertEquals(100, player.getCoins());
        assertEquals(100L, membership.getReputation());
        verify(playerFactionMapper, never()).updateById(any(PlayerFaction.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void contributionLocksFactionBeforeAddingReputation() {
        Player player = player(100);
        PlayerFaction membership = membership(100);
        when(playerFactionMapper.selectActiveByPlayerIdForUpdate(PLAYER_ID)).thenReturn(membership);
        when(contributionMapper.insert(any(FactionContribution.class))).thenReturn(1);
        when(playerFactionMapper.updateById(membership)).thenReturn(1);
        when(factionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(factionMapper.selectOne(any())).thenReturn(faction());
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);
        when(questService.addProgress(PLAYER_ID, "faction_contribution_value", 5,
                "explore", "event-1")).thenReturn(java.util.List.of());
        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);

        FactionContributionVO result = factionService.contribute(
                PLAYER_ID, null, "explore", 5L, "explore", "event-1", null);

        assertNotNull(result);
        assertEquals(105L, membership.getReputation());
        verify(playerFactionMapper).selectActiveByPlayerIdForUpdate(PLAYER_ID);
        verify(playerMapper, never()).selectByIdForUpdate(PLAYER_ID);
        verify(questService).addProgress(PLAYER_ID, "faction_contribution_value", 5,
                "explore", "event-1");
    }

    @Test
    void contributionForDifferentFactionIsIgnoredWithoutChangingReputation() {
        PlayerFaction membership = membership(100);
        when(playerFactionMapper.selectActiveByPlayerIdForUpdate(PLAYER_ID)).thenReturn(membership);

        assertNull(factionService.contribute(
                PLAYER_ID, "another_faction", "manual", 5L, "manual", null, null));

        assertEquals(100L, membership.getReputation());
        verify(contributionMapper, never()).insert(any(FactionContribution.class));
    }

    private Player player(int coins) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        player.setCoins(coins);
        return player;
    }

    private PlayerFaction membership(long reputation) {
        PlayerFaction membership = new PlayerFaction();
        membership.setPlayerId(PLAYER_ID);
        membership.setFactionKey(FACTION_KEY);
        membership.setStatus("active");
        membership.setReputation(reputation);
        membership.setContributionTotal(0L);
        return membership;
    }

    private FactionSkill skill(int coins, long reputation) {
        FactionSkill skill = new FactionSkill();
        skill.setFactionKey(FACTION_KEY);
        skill.setSkillKey(SKILL_KEY);
        skill.setName("深渊凝视");
        skill.setUnlockLevel(1);
        skill.setEnabled(1);
        skill.setCostJson("{\"coins\":" + coins + ",\"reputation\":" + reputation + "}");
        skill.setEffectsJson("{}");
        return skill;
    }

    private ConstellationFaction faction() {
        ConstellationFaction faction = new ConstellationFaction();
        faction.setFactionKey(FACTION_KEY);
        faction.setLevel(1);
        faction.setExp(0L);
        return faction;
    }
}
