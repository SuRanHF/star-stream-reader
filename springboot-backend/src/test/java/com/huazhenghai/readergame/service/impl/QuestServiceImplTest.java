package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.entity.PlayerQuest;
import com.huazhenghai.readergame.entity.Quest;
import com.huazhenghai.readergame.entity.QuestProgressLog;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerQuestMapper;
import com.huazhenghai.readergame.mapper.QuestMapper;
import com.huazhenghai.readergame.mapper.QuestProgressLogMapper;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.vo.QuestSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestServiceImplTest {

    @Mock private QuestMapper questMapper;
    @Mock private PlayerQuestMapper playerQuestMapper;
    @Mock private QuestProgressLogMapper progressLogMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private InventoryService inventoryService;
    @Mock private TitleService titleService;
    @Mock private AvatarRankService avatarRankService;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private QuestServiceImpl questService;

    @BeforeEach
    void setUp() {
        questService = newQuestService(new ObjectMapper());
    }

    private QuestServiceImpl newQuestService(ObjectMapper objectMapper) {
        return new QuestServiceImpl(
                questMapper, playerQuestMapper, progressLogMapper, playerMapper,
                playerLogMapper, inventoryService, titleService, avatarRankService,
                objectMapper, playerEffectService, actionReceiptService, resourceTransactionService);
    }

    @Test
    void levelAchievementTracksCurrentLevelAndRepeatedEvaluationIsIdempotent() {
        Player player = playerWithStats("{\"level\":3,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = activeQuest("achv_level_5", "achievement", 5);
        Quest quest = quest("achv_level_5", "level_reach", 5, "{}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerQuestMapper.selectList(any())).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);

        questService.evaluateGrowthQuests(1L);
        assertEquals(3, playerQuest.getProgress());
        assertEquals("active", playerQuest.getStatus());
        verify(playerQuestMapper, times(1)).updateById(playerQuest);
        verify(progressLogMapper, times(1)).insert(any(QuestProgressLog.class));

        questService.evaluateGrowthQuests(1L);
        assertEquals(3, playerQuest.getProgress());
        verify(playerQuestMapper, times(1)).updateById(playerQuest);
        verify(progressLogMapper, times(1)).insert(any(QuestProgressLog.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));

        player.setStatsJson("{\"level\":5,\"avatarRank\":\"F\"}");
        questService.evaluateGrowthQuests(1L);
        assertEquals(5, playerQuest.getProgress());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerQuestMapper, times(2)).updateById(playerQuest);
        verify(progressLogMapper, times(2)).insert(any(QuestProgressLog.class));
        verify(playerLogMapper).insert(any(PlayerLog.class));
    }

    @Test
    void questSummarySynchronizesGrowthAchievementsBeforeCountingClaimableQuests() {
        Player player = playerWithStats("{\"level\":5,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = activeQuest("achv_level_5", "achievement", 5);
        Quest quest = quest("achv_level_5", "level_reach", 5, "{}");
        when(questMapper.selectList(any())).thenReturn(java.util.List.of(quest));
        when(playerQuestMapper.selectCount(any())).thenReturn(1L);
        when(playerQuestMapper.selectList(any())).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerMapper.selectById(1L)).thenReturn(player);

        QuestSummaryVO summary = questService.getQuestSummary(1L);

        assertEquals(1, summary.getAchievementClaimable());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerQuestMapper).updateById(playerQuest);
        verify(progressLogMapper).insert(any(QuestProgressLog.class));
    }

    @Test
    void availableQuestListReturnsGrowthProgressAfterSynchronizingIt() {
        Player player = playerWithStats("{\"level\":5,\"avatarRank\":\"F\"}");
        player.setUserId(9L);
        PlayerQuest playerQuest = activeQuest("achv_level_5", "achievement", 5);
        Quest quest = quest("achv_level_5", "level_reach", 5, "{}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(questMapper.selectList(any())).thenReturn(java.util.List.of(quest));
        when(playerQuestMapper.selectCount(any())).thenReturn(1L);
        when(playerQuestMapper.selectList(any())).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);

        var quests = questService.getAvailableQuests(1L, 9L);

        assertEquals(1, quests.size());
        assertEquals(5, quests.get(0).getProgress());
        assertEquals("completed", quests.get(0).getStatus());
    }

    @Test
    void legacyRankAchievementUsesItsKeyWhenRankConditionIsMissing() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"D\"}");
        PlayerQuest playerQuest = activeQuest("achv_rank_C", "achievement", 1);
        Quest quest = quest("achv_rank_C", "avatar_rank_reach", 1, "{}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerQuestMapper.selectList(any())).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);

        questService.evaluateGrowthQuests(1L);
        assertEquals(0, playerQuest.getProgress());
        verify(playerQuestMapper, never()).updateById(playerQuest);
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));

        player.setStatsJson("{\"level\":1,\"avatarRank\":\"C\"}");
        questService.evaluateGrowthQuests(1L);
        assertEquals(1, playerQuest.getProgress());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerQuestMapper).updateById(playerQuest);
        verify(progressLogMapper).insert(any(QuestProgressLog.class));
        verify(playerLogMapper).insert(any(PlayerLog.class));
    }

    @Test
    void completingAnExplorationQuestWritesOneGameLogEntry() {
        PlayerQuest playerQuest = activeQuest("daily_explore_3", "daily", 3);
        playerQuest.setProgress(2);
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("探索世界");
        when(playerQuestMapper.selectActiveByPlayerForUpdate(1L)).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);
        when(progressLogMapper.insert(any(QuestProgressLog.class))).thenReturn(1);

        var changed = questService.addProgress(1L, "explore_count", 1, "explore", "event-1");

        assertEquals(1, changed.size());
        assertEquals("completed", changed.get(0).getStatus());
        verify(playerLogMapper).insert(any(PlayerLog.class));
        verify(playerQuestMapper).selectActiveByPlayerForUpdate(1L);
    }

    @Test
    void addProgressFailsWhenQuestRowWasNotUpdated() {
        PlayerQuest playerQuest = activeQuest("daily_explore_2", "daily", 2);
        Quest quest = quest("daily_explore_2", "explore_count", 2, "{}");
        when(playerQuestMapper.selectActiveByPlayerForUpdate(1L)).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> questService.addProgress(1L, "explore_count", 1, "explore", "event-1"));

        verify(progressLogMapper, never()).insert(any(QuestProgressLog.class));
    }

    @Test
    void addProgressFailsWhenProgressLogWasNotInserted() {
        PlayerQuest playerQuest = activeQuest("daily_explore_2", "daily", 2);
        Quest quest = quest("daily_explore_2", "explore_count", 2, "{}");
        when(playerQuestMapper.selectActiveByPlayerForUpdate(1L)).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(progressLogMapper.insert(any(QuestProgressLog.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> questService.addProgress(1L, "explore_count", 1, "explore", "event-1"));
    }

    @Test
    void addProgressFailsWhenCompletionLogWasNotInserted() {
        PlayerQuest playerQuest = activeQuest("daily_explore_1", "daily", 1);
        Quest quest = quest("daily_explore_1", "explore_count", 1, "{}");
        when(playerQuestMapper.selectActiveByPlayerForUpdate(1L)).thenReturn(java.util.List.of(playerQuest));
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> questService.addProgress(1L, "explore_count", 1, "explore", "event-1"));

        verify(progressLogMapper, never()).insert(any(QuestProgressLog.class));
    }

    @Test
    void questRewardCanOnlyBeClaimedOnce() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        player.setCoins(100);
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-24");
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("每日探索");
        quest.setRewardsJson("{\"coins\":15}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);

        var reward = questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L);

        assertEquals(true, reward.isClaimed());
        assertEquals(115, player.getCoins());
        assertEquals("claimed", playerQuest.getStatus());
        assertEquals(1, playerQuest.getRewardClaimed());

        ArgumentCaptor<PlayerLog> rewardLog = ArgumentCaptor.forClass(PlayerLog.class);
        verify(playerLogMapper).insert(rewardLog.capture());
        try {
            var metadata = new ObjectMapper().readTree(rewardLog.getValue().getMetadataJson());
            assertEquals("quest_reward", metadata.path("kind").asText());
            assertEquals("daily_explore_3", metadata.path("questKey").asText());
            assertEquals("2026-09-24", metadata.path("cycleKey").asText());
            assertEquals(15, metadata.path("rewards").path("coins").asInt());
        } catch (Exception exception) {
            throw new AssertionError("Quest reward log should preserve structured rewards", exception);
        }

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L));

        assertEquals("奖励已领取", duplicate.getMessage());
        assertEquals(115, player.getCoins());
        verify(playerMapper, times(2)).selectByIdForUpdate(1L);
        verify(playerMapper, times(1)).updateById(player);
        verify(playerQuestMapper, times(1)).updateById(playerQuest);
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChange =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(resourceChange.capture());
        assertEquals("currency", resourceChange.getValue().resourceType());
        assertEquals("coins", resourceChange.getValue().resourceKey());
        assertEquals(15L, resourceChange.getValue().amount());
        assertEquals(100L, resourceChange.getValue().balanceBefore());
        assertEquals(115L, resourceChange.getValue().balanceAfter());
        assertEquals("quest_reward", resourceChange.getValue().sourceType());
        verify(inventoryService, never()).addItem(any(), any(), anyInt());
    }

    @Test
    void questCoinRewardOverflowIsRejectedWithoutPersistingOrClaiming() {
        assertQuestRewardBalanceOverflow("{\"coins\":1}", Integer.MAX_VALUE, 0,
                "星币已达到可保存上限，暂时无法领取");
    }

    @Test
    void questStoryFragmentRewardOverflowIsRejectedWithoutPersistingOrClaiming() {
        assertQuestRewardBalanceOverflow("{\"storyFragments\":1}", 0, Integer.MAX_VALUE,
                "故事碎片已达到可保存上限，暂时无法领取");
    }

    @Test
    void questRewardRecordsCoinsFragmentsExperienceAndItemInOneClaimGroup() {
        Player player = playerWithStats("{\"exp\":99,\"level\":1,\"avatarRank\":\"F\",\"channelHeat\":2}");
        player.setCoins(100);
        player.setStoryFragments(4);
        PlayerQuest playerQuest = completedQuest("daily_bundle", "2026-09-24");
        Quest quest = quest("daily_bundle", "explore_count", 3, "{}");
        quest.setTitle("完整奖励");
        quest.setRewardsJson("{\"coins\":15,\"storyFragments\":2,\"exp\":1,\"channelHeat\":5,"
                + "\"items\":[{\"itemKey\":\"herb\",\"quantity\":2}]}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(avatarRankService.getExpMultiplier("F")).thenReturn(1.0);
        when(playerEffectService.applyChannelHeatGainRate(1L, 5)).thenReturn(5);
        when(inventoryService.addItemWithResult(1L, "herb", 2))
                .thenReturn(new ItemGrantResult(2, 5, "灵草"));
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);

        questService.claimReward(1L, "daily_bundle", "2026-09-24", 9L);

        ArgumentCaptor<PlayerResourceTransactionService.Change> changes =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService, times(5)).record(changes.capture());
        var byResource = changes.getAllValues().stream().collect(
                java.util.stream.Collectors.toMap(
                        change -> change.resourceType() + ":" + change.resourceKey(),
                        change -> change));
        assertEquals(15L, byResource.get("currency:coins").amount());
        assertEquals(100L, byResource.get("currency:coins").balanceBefore());
        assertEquals(115L, byResource.get("currency:coins").balanceAfter());
        assertEquals(2L, byResource.get("currency:story_fragments").amount());
        assertEquals(4L, byResource.get("currency:story_fragments").balanceBefore());
        assertEquals(6L, byResource.get("currency:story_fragments").balanceAfter());
        assertEquals(1L, byResource.get("experience:exp").amount());
        assertEquals(99L, byResource.get("experience:exp").balanceBefore());
        assertEquals(100L, byResource.get("experience:exp").balanceAfter());
        assertEquals(2L, byResource.get("item:herb").amount());
        assertEquals(3L, byResource.get("item:herb").balanceBefore());
        assertEquals(5L, byResource.get("item:herb").balanceAfter());
        assertEquals(5L, byResource.get("progress:channel_heat").amount());
        assertEquals(2L, byResource.get("progress:channel_heat").balanceBefore());
        assertEquals(7L, byResource.get("progress:channel_heat").balanceAfter());
        assertEquals(1, changes.getAllValues().stream()
                .map(PlayerResourceTransactionService.Change::transactionGroupId).distinct().count());
    }

    @Test
    void questRewardIsNotReportedClaimedWhenPlayerRewardPersistenceFails() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        player.setCoins(100);
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-24");
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("每日探索");
        quest.setRewardsJson("{\"coins\":15}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L));

        verify(playerQuestMapper, never()).updateById(playerQuest);
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void experienceRewardSerializationFailureDoesNotSaveOrClaimTheQuest() {
        assertRewardStatsSerializationFails("{\"coins\":15,\"exp\":10}", false);
    }

    @Test
    void channelHeatRewardSerializationFailureDoesNotSaveOrClaimTheQuest() {
        assertRewardStatsSerializationFails("{\"coins\":15,\"channelHeat\":5}", true);
    }

    @Test
    void questRewardIsNotReportedSuccessfulWhenClaimMarkerPersistenceFails() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        player.setCoins(100);
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-24");
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("每日探索");
        quest.setRewardsJson("{\"coins\":15}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L));

        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void questRewardIsNotReportedSuccessfulWhenRewardLogMetadataCannotBeSerialized() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        player.setCoins(100);
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-24");
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("每日探索");
        quest.setRewardsJson("{\"coins\":15}");
        questService = newQuestService(new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("模拟任务奖励日志元数据序列化失败") {};
            }
        });
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L));

        assertEquals("任务奖励日志元数据序列化失败", error.getMessage());
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void questRewardIsNotReportedSuccessfulWhenRewardLogInsertFails() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        player.setCoins(100);
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-24");
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("每日探索");
        quest.setRewardsJson("{\"coins\":15}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(0);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L));

        assertEquals("任务奖励日志写入失败", error.getMessage());
        verify(playerLogMapper).insert(any(PlayerLog.class));
    }

    @Test
    void emptyQuestRewardCanBeClaimedWithoutUpdatingPlayerRow() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = completedQuest("daily_empty", "2026-09-24");
        Quest quest = quest("daily_empty", "explore_count", 1, "{}");
        quest.setTitle("空奖励任务");
        quest.setRewardsJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);

        var reward = questService.claimReward(1L, "daily_empty", "2026-09-24", 9L);

        assertEquals(true, reward.isClaimed());
        assertEquals("claimed", playerQuest.getStatus());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper).updateById(playerQuest);
        verify(playerLogMapper).insert(any(PlayerLog.class));
    }

    @Test
    void claimingExperienceRewardUsesSharedLevelProgressionAndWritesMilestone() throws Exception {
        Player player = playerWithStats("{\"exp\":99,\"level\":1,\"freePoints\":2,\"hp\":40,\"maxHp\":100,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = completedQuest("daily_exp", "2026-09-24");
        Quest quest = quest("daily_exp", "explore_count", 3, "{}");
        quest.setTitle("修为增长");
        quest.setRewardsJson("{\"exp\":1}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(avatarRankService.getExpMultiplier("F")).thenReturn(1.0);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerQuestMapper.updateById(playerQuest)).thenReturn(1);
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);

        var reward = questService.claimReward(1L, "daily_exp", "2026-09-24", 9L);

        var stats = new ObjectMapper().readTree(player.getStatsJson());
        assertEquals(1, reward.getEarned().get("exp"));
        assertEquals(100, stats.path("exp").asInt());
        assertEquals(2, stats.path("level").asInt());
        assertEquals(5, stats.path("freePoints").asInt());
        assertEquals(100, stats.path("hp").asInt());
        assertEquals(400, stats.path("maxExp").asInt());

        ArgumentCaptor<PlayerLog> logCaptor = ArgumentCaptor.forClass(PlayerLog.class);
        verify(playerLogMapper, times(2)).insert(logCaptor.capture());
        assertEquals("milestone", logCaptor.getAllValues().get(0).getType());
        assertEquals("你的修为提升至等级 2，获得 3 点自由属性点。",
                logCaptor.getAllValues().get(0).getMessage());
    }

    @Test
    void questClaimIsNotConsumedWhenAnItemRewardCannotFitTheInventory() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = completedQuest("daily_item", "2026-09-24");
        Quest quest = quest("daily_item", "explore_count", 3, "{}");
        quest.setRewardsJson("{\"items\":[{\"itemKey\":\"herb\",\"quantity\":2}]}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        when(inventoryService.addItemWithResult(1L, "herb", 2))
                .thenReturn(new ItemGrantResult(1, 1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> questService.claimReward(1L, "daily_item", "2026-09-24", 9L));

        assertEquals("背包堆叠空间不足或奖励物品不可用，处理后再领取任务奖励", error.getMessage());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper, never()).updateById(any(PlayerQuest.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void questClaimIsNotConsumedWhenTitleGrantFails() {
        Player player = playerWithStats("{\"level\":5,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = completedQuest("achv_level_5", "none");
        Quest quest = quest("achv_level_5", "level_reach", 5, "{}");
        quest.setTitle("初露锋芒");
        quest.setRewardsJson("{\"titleKey\":\"growth_novice\"}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "称号配置不存在: growth_novice"))
                .when(titleService).grantTitle(1L, "growth_novice");

        BusinessException error = assertThrows(BusinessException.class,
                () -> questService.claimReward(1L, "achv_level_5", "none", 9L));

        assertEquals("称号配置不存在: growth_novice", error.getMessage());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper, never()).updateById(any(PlayerQuest.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void numericQuestIdCannotClaimAnotherPlayersReward() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        PlayerQuest foreignQuest = completedQuest("daily_explore_3", "2026-09-24");
        foreignQuest.setId(77L);
        foreignQuest.setPlayerId(2L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectByIdForUpdate(77L)).thenReturn(foreignQuest);

        BusinessException error = assertThrows(BusinessException.class,
                () -> questService.claimReward(1L, "77", null, 9L));

        assertEquals("任务不存在", error.getMessage());
        verify(questMapper, never()).selectOne(any());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper, never()).updateById(any(PlayerQuest.class));
        verify(inventoryService, never()).addItem(any(), any(), anyInt());
    }

    @Test
    void expiredQuestReturnsAnExpiredMessageWithoutGrantingRewards() {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-23");
        playerQuest.setStatus("expired");
        playerQuest.setRewardClaimed(0);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);

        BusinessException error = assertThrows(BusinessException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-23", 9L));

        assertEquals("任务已过期", error.getMessage());
        verify(questMapper, never()).selectOne(any());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper, never()).updateById(any(PlayerQuest.class));
    }

    @Test
    void expiringCompletedQuestWritesOneHistoryNoticeButDoesNotGrantItsReward() throws Exception {
        PlayerQuest completed = completedQuest("daily_explore_3", "2026-09-24");
        PlayerQuest unfinished = activeQuest("daily_combat_3", "daily", 3);
        unfinished.setCycleKey("2026-09-24");
        unfinished.setRewardClaimed(0);
        Quest definition = quest("daily_explore_3", "explore_count", 3, "{}");
        definition.setTitle("探索世界");
        when(playerQuestMapper.selectList(any())).thenReturn(java.util.List.of(completed, unfinished));
        when(questMapper.selectList(any())).thenReturn(java.util.List.of(definition));
        when(playerQuestMapper.updateById(any(PlayerQuest.class))).thenReturn(1);

        int expired = questService.refreshDailyQuests();

        assertEquals(2, expired);
        assertEquals("expired", completed.getStatus());
        assertEquals("expired", unfinished.getStatus());
        verify(playerQuestMapper, times(2)).updateById(any(PlayerQuest.class));
        ArgumentCaptor<PlayerLog> logCaptor = ArgumentCaptor.forClass(PlayerLog.class);
        verify(playerLogMapper).insert(logCaptor.capture());
        assertEquals("任务「探索世界」已过期，未领取奖励已失效", logCaptor.getValue().getMessage());
        var metadata = new ObjectMapper().readTree(logCaptor.getValue().getMetadataJson());
        assertEquals("quest_expired", metadata.path("kind").asText());
        assertEquals("2026-09-24", metadata.path("cycleKey").asText());
        assertEquals(3, metadata.path("progress").asInt());
        verify(inventoryService, never()).addItem(any(), any(), anyInt());
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    private static Player playerWithStats(String statsJson) {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson(statsJson);
        return player;
    }

    private void assertRewardStatsSerializationFails(String rewardsJson, boolean includesChannelHeat) {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\",\"channelHeat\":0}");
        player.setCoins(100);
        PlayerQuest playerQuest = completedQuest("daily_explore_3", "2026-09-24");
        Quest quest = quest("daily_explore_3", "explore_count", 3, "{}");
        quest.setTitle("每日探索");
        quest.setRewardsJson(rewardsJson);
        questService = newQuestService(new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("模拟任务奖励状态序列化失败") {};
            }
        });
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);
        if (includesChannelHeat) {
            when(playerEffectService.applyChannelHeatGainRate(1L, 5)).thenReturn(5);
        } else {
            when(avatarRankService.getExpMultiplier("F")).thenReturn(1.0);
        }

        assertThrows(IllegalStateException.class,
                () -> questService.claimReward(1L, "daily_explore_3", "2026-09-24", 9L));

        assertEquals("{\"level\":1,\"avatarRank\":\"F\",\"channelHeat\":0}", player.getStatsJson());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper, never()).updateById(any(PlayerQuest.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    private void assertQuestRewardBalanceOverflow(String rewardsJson, int startingCoins,
                                                   int startingFragments, String expectedMessage) {
        Player player = playerWithStats("{\"level\":1,\"avatarRank\":\"F\"}");
        player.setCoins(startingCoins);
        player.setStoryFragments(startingFragments);
        PlayerQuest playerQuest = completedQuest("daily_overflow", "2026-09-24");
        Quest quest = quest("daily_overflow", "explore_count", 1, "{}");
        quest.setTitle("边界奖励");
        quest.setRewardsJson(rewardsJson);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerQuestMapper.selectOne(any())).thenReturn(playerQuest);
        when(questMapper.selectOne(any())).thenReturn(quest);

        BusinessException error = assertThrows(BusinessException.class,
                () -> questService.claimReward(1L, "daily_overflow", "2026-09-24", 9L));

        assertEquals(expectedMessage, error.getMessage());
        assertEquals(startingCoins, player.getCoins());
        assertEquals(startingFragments, player.getStoryFragments());
        assertEquals("completed", playerQuest.getStatus());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerQuestMapper, never()).updateById(any(PlayerQuest.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
        verify(resourceTransactionService, never()).record(any());
        verify(inventoryService, never()).addItemWithResult(any(), any(), anyInt());
    }

    private static PlayerQuest activeQuest(String key, String type, int target) {
        PlayerQuest playerQuest = new PlayerQuest();
        playerQuest.setPlayerId(1L);
        playerQuest.setQuestKey(key);
        playerQuest.setQuestType(type);
        playerQuest.setStatus("active");
        playerQuest.setProgress(0);
        playerQuest.setTargetValue(target);
        return playerQuest;
    }

    private static PlayerQuest completedQuest(String key, String cycleKey) {
        PlayerQuest playerQuest = activeQuest(key, "daily", 3);
        playerQuest.setId(11L);
        playerQuest.setStatus("completed");
        playerQuest.setProgress(3);
        playerQuest.setRewardClaimed(0);
        playerQuest.setCycleKey(cycleKey);
        return playerQuest;
    }

    private static Quest quest(String key, String targetType, int target, String conditionsJson) {
        Quest quest = new Quest();
        quest.setQuestKey(key);
        quest.setTitle(key);
        quest.setQuestType("achievement");
        quest.setTargetType(targetType);
        quest.setTargetValue(target);
        quest.setConditionsJson(conditionsJson);
        return quest;
    }
}
