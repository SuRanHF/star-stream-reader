package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerSkill;
import com.huazhenghai.readergame.entity.Skill;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerSkillMapper;
import com.huazhenghai.readergame.mapper.SkillMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.PlayerSkillVO;
import com.huazhenghai.readergame.vo.SkillVO;
import com.huazhenghai.readergame.vo.SkillSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private SkillMapper skillMapper;
    @Mock private PlayerSkillMapper playerSkillMapper;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private SkillServiceImpl skillService;

    @BeforeEach
    void setUp() {
        skillService = new SkillServiceImpl(
                playerMapper, skillMapper, playerSkillMapper, playerLogService,
                actionReceiptService, resourceTransactionService, new ObjectMapper());
    }

    @Test
    void unlockingSkillLocksPlayerBeforeCheckingAndSpendingResources() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(100);
        player.setStoryFragments(10);
        player.setStatsJson("{\"level\":1,\"avatarRank\":\"F\",\"isResting\":false}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        Skill skill = new Skill();
        skill.setSkillKey("archive_pulse");
        skill.setName("档案脉冲");
        skill.setType("passive");
        skill.setRarity("common");
        skill.setEnabled(1);
        skill.setCostJson("{\"coins\":40,\"storyFragments\":3}");
        skill.setUnlockConditionsJson("{}");
        skill.setEffectsJson("{\"attack\":2}");
        when(skillMapper.selectOne(any())).thenReturn(skill);
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);
        when(playerSkillMapper.insert(any(PlayerSkill.class))).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);

        skillService.unlockSkill(1L, "archive_pulse", 9L, "skill-unlock-first");

        assertEquals(60, player.getCoins());
        assertEquals(7, player.getStoryFragments());
        verify(playerMapper).selectByIdForUpdate(1L);
        ArgumentCaptor<PlayerSkill> learnedSkill = ArgumentCaptor.forClass(PlayerSkill.class);
        verify(playerSkillMapper).insert(learnedSkill.capture());
        assertEquals("archive_pulse", learnedSkill.getValue().getSkillKey());
        verify(playerMapper).updateById(player);
        ArgumentCaptor<Map<String, Object>> logMetadata = ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("skill_unlock"),
                eq("习得技能: 档案脉冲"), logMetadata.capture());
        assertEquals("skill_unlock", logMetadata.getValue().get("kind"));
        assertEquals(Map.of("coins", 40, "storyFragments", 3), logMetadata.getValue().get("costs"));
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChanges =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        org.mockito.Mockito.verify(resourceTransactionService, org.mockito.Mockito.times(2))
                .record(resourceChanges.capture());
        assertEquals("currency", resourceChanges.getAllValues().get(0).resourceType());
        assertEquals("coins", resourceChanges.getAllValues().get(0).resourceKey());
        assertEquals(-40L, resourceChanges.getAllValues().get(0).amount());
        assertEquals(100L, resourceChanges.getAllValues().get(0).balanceBefore());
        assertEquals(60L, resourceChanges.getAllValues().get(0).balanceAfter());
        assertEquals("story_fragments", resourceChanges.getAllValues().get(1).resourceKey());
        assertEquals(-3L, resourceChanges.getAllValues().get(1).amount());
        assertEquals(10L, resourceChanges.getAllValues().get(1).balanceBefore());
        assertEquals(7L, resourceChanges.getAllValues().get(1).balanceAfter());
        assertEquals(resourceChanges.getAllValues().get(0).transactionGroupId(),
                resourceChanges.getAllValues().get(1).transactionGroupId());
        verify(actionReceiptService).record(eq(1L), eq("skill.unlock"), eq("skill-unlock-first"),
                eq(Map.of("playerId", 1L, "skillKey", "archive_pulse")), any());
    }

    @Test
    void unlockDoesNotReportSuccessWhenSkillRecordInsertFails() {
        Player player = playerAtRank("F");
        Skill skill = skill("archive_pulse");
        skill.setCostJson("{\"coins\":40}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(skillMapper.selectOne(any())).thenReturn(skill);
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);
        when(playerSkillMapper.insert(any(PlayerSkill.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> skillService.unlockSkill(1L, "archive_pulse", 9L, "skill-unlock-insert-fail"));

        verify(playerMapper, org.mockito.Mockito.never()).updateById(any(Player.class));
        verify(playerLogService, org.mockito.Mockito.never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void unlockDoesNotReportSuccessWhenResourceDeductionFails() {
        Player player = playerAtRank("F");
        Skill skill = skill("archive_pulse");
        skill.setCostJson("{\"coins\":40}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(skillMapper.selectOne(any())).thenReturn(skill);
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);
        when(playerSkillMapper.insert(any(PlayerSkill.class))).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> skillService.unlockSkill(1L, "archive_pulse", 9L, "skill-unlock-resource-fail"));

        verify(playerLogService, org.mockito.Mockito.never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void unlockableSkillsRequireEnoughCoinsAndStoryFragments() {
        Player player = playerAtRank("D");
        player.setCoins(50);
        player.setStoryFragments(2);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerSkillMapper.selectList(any())).thenReturn(java.util.List.of());

        Skill affordable = skill("affordable");
        affordable.setCostJson("{\"coins\":40,\"storyFragments\":2}");
        Skill shortOnCoins = skill("short_on_coins");
        shortOnCoins.setCostJson("{\"coins\":51,\"storyFragments\":1}");
        Skill shortOnFragments = skill("short_on_fragments");
        shortOnFragments.setCostJson("{\"coins\":1,\"storyFragments\":3}");
        when(skillMapper.selectList(any())).thenReturn(
                java.util.List.of(affordable, shortOnCoins, shortOnFragments));

        java.util.List<SkillVO> unlockable = skillService.evaluateUnlockableSkills(1L);

        assertEquals(1, unlockable.size());
        assertEquals("affordable", unlockable.get(0).getSkillKey());
        assertTrue(unlockable.get(0).isUnlockable());
        assertEquals(40, unlockable.get(0).getCost().get("coins"));
    }

    @Test
    void restingPlayerHasNoCurrentlyUnlockableSkills() {
        Player player = playerAtRank("D");
        player.setStatsJson("{\"avatarRank\":\"D\",\"isResting\":true}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerSkillMapper.selectList(any())).thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), skillService.evaluateUnlockableSkills(1L));
    }

    @Test
    void skillSummaryCountsOnlySkillsAffordableRightNow() {
        Player player = playerAtRank("D");
        player.setCoins(10);
        player.setStoryFragments(0);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerSkillMapper.selectList(any())).thenReturn(java.util.List.of());
        when(playerSkillMapper.selectCount(any())).thenReturn(0L);

        Skill costly = skill("costly");
        costly.setCostJson("{\"coins\":20}");
        when(skillMapper.selectList(any())).thenReturn(java.util.List.of(costly));

        SkillSummaryVO summary = skillService.getSkillSummary(1L);

        assertEquals(0, summary.getUnlockableCount());
    }

    @Test
    void playerBelowRankDHasNoSkillSlots() {
        Player player = playerAtRank("E");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerSkillMapper.selectOne(any())).thenReturn(learnedSkill("steady_breath"));
        when(skillMapper.selectOne(any())).thenReturn(skill("steady_breath"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> skillService.equipSkill(1L, "steady_breath", 9L));

        assertEquals(ErrorCode.SKILL_SLOT_FULL, error.getCode());
        verify(playerSkillMapper, org.mockito.Mockito.never()).updateById(any(PlayerSkill.class));
    }

    @Test
    void rankDAllowsOneEquippedSkillButRejectsAnother() {
        Player player = playerAtRank("D");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerSkillMapper.selectOne(any())).thenReturn(
                learnedSkill("steady_breath"), learnedSkill("iron_resolve"));
        when(skillMapper.selectOne(any())).thenReturn(
                skill("steady_breath"), skill("iron_resolve"));
        when(playerSkillMapper.selectCount(any())).thenReturn(0L, 1L);
        when(playerSkillMapper.updateById(any(PlayerSkill.class))).thenReturn(1);

        assertEquals(1, skillService.equipSkill(1L, "steady_breath", 9L).getEquipped());
        BusinessException error = assertThrows(BusinessException.class,
                () -> skillService.equipSkill(1L, "iron_resolve", 9L));

        assertEquals(ErrorCode.SKILL_SLOT_FULL, error.getCode());
    }

    @Test
    void unlockReturnsStoredResponseBeforeCheckingAlreadyLearnedState() {
        PlayerSkillVO replay = new PlayerSkillVO();
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(playerAtRank("F"));
        when(actionReceiptService.findReplay(eq(1L), eq("skill.unlock"), eq("skill-unlock-replay"),
                eq(Map.of("playerId", 1L, "skillKey", "archive_pulse")), eq(PlayerSkillVO.class)))
                .thenReturn(replay);

        PlayerSkillVO result = skillService.unlockSkill(1L, "archive_pulse", 9L, "skill-unlock-replay");

        assertSame(replay, result);
        verify(playerSkillMapper, never()).selectCount(any());
        verify(playerSkillMapper, never()).insert(any(PlayerSkill.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void disabledSkillCannotBeEquipped() {
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(playerAtRank("D"));
        when(playerSkillMapper.selectOne(any())).thenReturn(learnedSkill("disabled_skill"));
        Skill disabled = skill("disabled_skill");
        disabled.setEnabled(0);
        when(skillMapper.selectOne(any())).thenReturn(disabled);

        BusinessException error = assertThrows(BusinessException.class,
                () -> skillService.equipSkill(1L, "disabled_skill", 9L));

        assertEquals(ErrorCode.SKILL_DISABLED, error.getCode());
        verify(playerSkillMapper, org.mockito.Mockito.never()).updateById(any(PlayerSkill.class));
    }

    @Test
    void equippedDisabledSkillDoesNotContributeBonusesOrBonusDetails() {
        PlayerSkill equipped = learnedSkill("disabled_skill");
        equipped.setEquipped(1);
        when(playerSkillMapper.selectList(any())).thenReturn(java.util.List.of(equipped));
        Skill disabled = skill("disabled_skill");
        disabled.setEnabled(0);
        disabled.setName("已停用技能");
        disabled.setEffectsJson("{\"attack\":50,\"maxHp\":100}");
        when(skillMapper.selectOne(any())).thenReturn(disabled);

        assertEquals(Map.of(), skillService.calculateSkillBonus(1L));
        assertEquals(Map.of(), skillService.getSkillBonusDetail(1L));
    }

    @Test
    void skillSummaryReportsRankSlotCapacityAndUsage() {
        when(playerMapper.selectById(1L)).thenReturn(playerAtRank("C"));
        when(playerSkillMapper.selectList(any())).thenReturn(java.util.List.of());
        when(skillMapper.selectList(any())).thenReturn(java.util.List.of());
        when(playerSkillMapper.selectCount(any())).thenReturn(1L);

        SkillSummaryVO summary = skillService.getSkillSummary(1L);

        assertEquals(1, summary.getEquippedSkillCount());
        assertEquals(2, summary.getSkillSlotLimit());
    }

    private Player playerAtRank(String rank) {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(100);
        player.setStoryFragments(10);
        player.setStatsJson("{\"avatarRank\":\"" + rank + "\",\"isResting\":false}");
        return player;
    }

    private PlayerSkill learnedSkill(String skillKey) {
        PlayerSkill learned = new PlayerSkill();
        learned.setPlayerId(1L);
        learned.setSkillKey(skillKey);
        learned.setLevel(1);
        learned.setEquipped(0);
        return learned;
    }

    private Skill skill(String skillKey) {
        Skill skill = new Skill();
        skill.setSkillKey(skillKey);
        skill.setName("稳定呼吸");
        skill.setType("passive");
        skill.setRarity("common");
        skill.setEnabled(1);
        skill.setCostJson("{}");
        skill.setUnlockConditionsJson("{}");
        skill.setEffectsJson("{\"maxStamina\":5}");
        return skill;
    }
}
