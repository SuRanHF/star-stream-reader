package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerSkillMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.entity.AvatarRankConfig;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerSkill;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarRankServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerSkillMapper playerSkillMapper;
    @Mock private PlayerTitleMapper playerTitleMapper;
    @Mock private AvatarRankConfigMapper rankConfigMapper;
    @Mock private TitleMapper titleMapper;
    @Mock private RankingMapper rankingMapper;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private AvatarRankServiceImpl avatarRankService;

    @BeforeEach
    void setUp() {
        avatarRankService = new AvatarRankServiceImpl(
                playerMapper, playerSkillMapper, playerTitleMapper, rankConfigMapper, titleMapper,
                rankingMapper, playerLogService, new ObjectMapper(), playerEffectService, resourceTransactionService);
    }

    @Test
    void leaderboardUsesDatabaseOrderedRowsAndAuthoritativePvpRating() {
        Map<String, Object> topPlayer = new LinkedHashMap<>();
        topPlayer.put("playerId", 17L);
        topPlayer.put("playerName", "Top player");
        topPlayer.put("avatarRank", "C");
        topPlayer.put("level", 8);
        topPlayer.put("rating", 1240);
        List<Map<String, Object>> expected = List.of(topPlayer);
        when(rankingMapper.selectAvatarRankLeaderboard(200)).thenReturn(expected);

        List<Map<String, Object>> actual = avatarRankService.getAvatarRankLeaderboard(200);

        assertSame(expected, actual);
        verify(rankingMapper).selectAvatarRankLeaderboard(200);
    }

    @Test
    void leaderboardRejectsLimitsOutsideTheDocumentedRange() {
        assertThrows(BusinessException.class, () -> avatarRankService.getAvatarRankLeaderboard(0));
        assertThrows(BusinessException.class, () -> avatarRankService.getAvatarRankLeaderboard(201));
        org.mockito.Mockito.verifyNoInteractions(rankingMapper);
    }

    @Test
    void booleanTitleRequirementIsDisplayedAsOneAndCannotBeBypassedDuringRankUp() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"A\",\"level\":30}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerTitleMapper.selectList(any())).thenReturn(List.of());

        AvatarRankConfig currentRank = rank("A", "A级·故事承载者", "S");
        AvatarRankConfig nextRank = rank("S", "S级·星流观测者", "SS");
        nextRank.setRequirementsJson("[{\"type\":\"rareTitleRequired\",\"value\":true,"
                + "\"label\":\"拥有至少一个稀有称号\"}]");
        when(rankConfigMapper.selectOne(any())).thenReturn(currentRank, nextRank, currentRank, nextRank);

        var progress = avatarRankService.getAvatarRankInfo(1L);

        assertEquals(false, progress.isCanRankUp());
        assertEquals(0, progress.getRequirements().getFirst().getCurrent());
        assertEquals(1, progress.getRequirements().getFirst().getRequired(),
                "布尔型必需条件应显示为 0/1，而不是 0/0");
        assertEquals(false, progress.getRequirements().getFirst().isCompleted());

        BusinessException error = assertThrows(BusinessException.class,
                () -> avatarRankService.rankUp(1L, 9L));

        assertEquals(ErrorCode.RANK_REQUIREMENTS_NOT_MET, error.getCode());
        org.mockito.Mockito.verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
    }

    @Test
    void rankUpAppliesConfiguredBonusesAndWritesItsMilestoneLog() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"F\",\"freePoints\":0,"
                + "\"maxHp\":100,\"maxStamina\":100,\"channelHeat\":0,"
                + "\"storyGrade\":\"ordinary\"}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerEffectService.applyChannelHeatGainRate(1L, 20)).thenReturn(20);
        when(playerMapper.selectById(1L)).thenReturn(player);

        AvatarRankConfig currentRank = rank("F", "临时化身", "E");
        AvatarRankConfig nextRank = rank("E", "E级·剧本幸存者", "D");
        nextRank.setRequirementsJson("[]");
        nextRank.setRewardsJson("{\"stats\":{\"maxHp\":20,\"maxStamina\":10},"
                + "\"channelHeat\":20,\"log\":\"你已晋升为剧本幸存者。\"}");
        when(rankConfigMapper.selectOne(any()))
                .thenReturn(currentRank, nextRank, nextRank, null);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(resourceTransactionService.record(any())).thenReturn(true);

        var result = avatarRankService.rankUp(1L, 9L);

        var updatedStats = new ObjectMapper().readTree(player.getStatsJson());
        assertEquals("E", updatedStats.path("avatarRank").asText());
        assertEquals(100, updatedStats.path("freePoints").asInt());
        assertEquals(120, updatedStats.path("maxHp").asInt());
        assertEquals(110, updatedStats.path("maxStamina").asInt());
        assertEquals(20, updatedStats.path("channelHeat").asInt());
        assertEquals("E", result.getCurrentRank().get("key"));
        verify(playerMapper).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(playerLogService).addLog(1L, "system", "你已晋升为剧本幸存者。");
        ArgumentCaptor<PlayerResourceTransactionService.Change> heatChange =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(heatChange.capture());
        assertEquals("progress", heatChange.getValue().resourceType());
        assertEquals("channel_heat", heatChange.getValue().resourceKey());
        assertEquals(20L, heatChange.getValue().amount());
        assertEquals(0L, heatChange.getValue().balanceBefore());
        assertEquals(20L, heatChange.getValue().balanceAfter());
        assertEquals("avatar_rank_up", heatChange.getValue().sourceType());
    }

    @Test
    void prestigeResetsRankAndUnequipsSkillsWithoutDeletingLearnedSkills() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"SSS\",\"prestigeLevel\":\"2\",\"exp\":250}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerSkill equippedSkill = new PlayerSkill();
        equippedSkill.setId(77L);
        equippedSkill.setPlayerId(1L);
        equippedSkill.setSkillKey("archive_pulse");
        equippedSkill.setEquipped(1);
        when(playerSkillMapper.selectList(any())).thenReturn(List.of(equippedSkill));
        when(playerSkillMapper.updateById(equippedSkill)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(resourceTransactionService.record(any())).thenReturn(true);

        Map<String, Object> result = avatarRankService.prestige(1L, 9L);

        var updatedStats = new ObjectMapper().readTree(player.getStatsJson());
        assertEquals("F", updatedStats.path("avatarRank").asText());
        assertEquals(3, updatedStats.path("prestigeLevel").asInt());
        assertEquals(0, updatedStats.path("exp").asInt());
        assertEquals(0, equippedSkill.getEquipped());
        assertEquals(1, result.get("unequippedSkillCount"));
        assertEquals(250, result.get("experienceCleared"));
        verify(playerSkillMapper).updateById(equippedSkill);
        verify(playerMapper).updateById(player);
        ArgumentCaptor<PlayerResourceTransactionService.Change> experienceChange =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(experienceChange.capture());
        assertEquals("experience", experienceChange.getValue().resourceType());
        assertEquals("exp", experienceChange.getValue().resourceKey());
        assertEquals(-250L, experienceChange.getValue().amount());
        assertEquals(250L, experienceChange.getValue().balanceBefore());
        assertEquals(0L, experienceChange.getValue().balanceAfter());
        assertEquals("avatar_prestige", experienceChange.getValue().sourceType());
    }

    @Test
    void prestigeDoesNotProceedWhenExperienceLedgerRejectsReset() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"SSS\",\"prestigeLevel\":2,\"exp\":250}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(resourceTransactionService.record(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> avatarRankService.prestige(1L, 9L));

        assertEquals("{\"avatarRank\":\"SSS\",\"prestigeLevel\":2,\"exp\":250}",
                player.getStatsJson());
        org.mockito.Mockito.verify(playerSkillMapper, org.mockito.Mockito.never()).selectList(any());
        org.mockito.Mockito.verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
    }

    @Test
    void prestigeRequiresSssRankBeforeChangingSkillsOrPlayer() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"A\"}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        BusinessException error = assertThrows(BusinessException.class,
                () -> avatarRankService.prestige(1L, 9L));

        assertEquals(ErrorCode.RANK_REQUIREMENTS_NOT_MET, error.getCode());
        verify(playerSkillMapper, org.mockito.Mockito.never()).selectList(any());
        verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
    }

    @Test
    void rankUpDoesNotReturnSuccessWhenPlayerProgressCannotBeSaved() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"F\"}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        AvatarRankConfig currentRank = rank("F", "临时化身", "E");
        AvatarRankConfig nextRank = rank("E", "E级·剧本幸存者", "D");
        nextRank.setRequirementsJson("[]");
        nextRank.setRewardsJson("{}");
        when(rankConfigMapper.selectOne(any())).thenReturn(currentRank, nextRank);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> avatarRankService.rankUp(1L, 9L));

        org.mockito.Mockito.verify(playerLogService, org.mockito.Mockito.never())
                .addLog(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rankUpRejectsFreePointOverflowBeforeSavingOrLogging() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"F\",\"freePoints\":2147483647}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        AvatarRankConfig currentRank = rank("F", "临时化身", "E");
        AvatarRankConfig nextRank = rank("E", "E级·剧本幸存者", "D");
        nextRank.setRequirementsJson("[]");
        nextRank.setRewardsJson("{}");
        when(rankConfigMapper.selectOne(any())).thenReturn(currentRank, nextRank);

        assertThrows(BusinessException.class, () -> avatarRankService.rankUp(1L, 9L));

        org.mockito.Mockito.verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
        org.mockito.Mockito.verify(playerLogService, org.mockito.Mockito.never())
                .addLog(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rankUpRejectsConfiguredStatOverflowBeforeSavingOrLogging() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"F\",\"freePoints\":0,\"maxHp\":2147483647}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        AvatarRankConfig currentRank = rank("F", "临时化身", "E");
        AvatarRankConfig nextRank = rank("E", "E级·剧本幸存者", "D");
        nextRank.setRequirementsJson("[]");
        nextRank.setRewardsJson("{\"stats\":{\"maxHp\":1}}");
        when(rankConfigMapper.selectOne(any())).thenReturn(currentRank, nextRank);

        assertThrows(BusinessException.class, () -> avatarRankService.rankUp(1L, 9L));

        org.mockito.Mockito.verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
        org.mockito.Mockito.verify(playerLogService, org.mockito.Mockito.never())
                .addLog(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rankUpStopsBeforeSavingOrLoggingWhenProgressSerializationFails() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"F\"}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        AvatarRankConfig currentRank = rank("F", "临时化身", "E");
        AvatarRankConfig nextRank = rank("E", "E级·剧本幸存者", "D");
        nextRank.setRequirementsJson("[]");
        nextRank.setRewardsJson("{}");
        when(rankConfigMapper.selectOne(any())).thenReturn(currentRank, nextRank);
        avatarRankService = new AvatarRankServiceImpl(
                playerMapper, playerSkillMapper, playerTitleMapper, rankConfigMapper, titleMapper,
                rankingMapper, playerLogService, new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("模拟位阶状态序列化失败") {};
                    }
                }, playerEffectService, resourceTransactionService);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> avatarRankService.rankUp(1L, 9L));

        assertEquals("位阶晋升状态序列化失败", error.getMessage());
        assertEquals("{\"avatarRank\":\"F\"}", player.getStatsJson());
        org.mockito.Mockito.verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
        org.mockito.Mockito.verify(playerLogService, org.mockito.Mockito.never())
                .addLog(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void prestigeDoesNotReportSuccessWhenUnequippingASkillFails() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"avatarRank\":\"SSS\"}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        PlayerSkill equipped = new PlayerSkill();
        equipped.setPlayerId(1L);
        equipped.setSkillKey("archive_pulse");
        equipped.setEquipped(1);
        when(playerSkillMapper.selectList(any())).thenReturn(List.of(equipped));
        when(playerSkillMapper.updateById(equipped)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> avatarRankService.prestige(1L, 9L));

        org.mockito.Mockito.verify(playerMapper, org.mockito.Mockito.never()).updateById(player);
        org.mockito.Mockito.verify(playerLogService, org.mockito.Mockito.never())
                .addLog(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private static AvatarRankConfig rank(String key, String name, String nextKey) {
        AvatarRankConfig rank = new AvatarRankConfig();
        rank.setRankKey(key);
        rank.setRankName(name);
        rank.setDisplayName(name);
        rank.setOrderNum("F".equals(key) ? 1 : 2);
        rank.setNextRankKey(nextKey);
        rank.setRequirementsJson("[]");
        rank.setRewardsJson("{}");
        rank.setEnabled(1);
        return rank;
    }
}
