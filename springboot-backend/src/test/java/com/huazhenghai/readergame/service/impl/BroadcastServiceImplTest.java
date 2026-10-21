package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.BroadcastContribution;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.BroadcastContributionMapper;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceImplTest {

    private static final Long PLAYER_ID = 31L;
    private static final String EVENT_KEY = "event-test";

    @Mock private BroadcastEventMapper eventMapper;
    @Mock private BroadcastContributionMapper contributionMapper;
    @Mock private WorldlineService worldlineService;
    @Mock private ChatService chatService;
    @Mock private PlayerMapper playerMapper;
    @Mock private InventoryService inventoryService;
    @Mock private WebSocketSessionManager sessionManager;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private BroadcastServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BroadcastServiceImpl(eventMapper, contributionMapper, worldlineService,
                chatService, playerMapper, inventoryService, new ObjectMapper(), sessionManager,
                playerEffectService, resourceTransactionService);
    }

    @Test
    void activeBroadcastListExcludesExpiredAndNotYetStartedEvents() {
        LocalDateTime now = LocalDateTime.now();
        BroadcastEvent expired = activeEvent("expired", now.minusDays(1), now.minusSeconds(1));
        BroadcastEvent upcoming = activeEvent("upcoming", now.plusMinutes(1), now.plusHours(1));
        BroadcastEvent live = activeEvent("live", now.minusMinutes(1), now.plusMinutes(1));
        BroadcastEvent timeless = activeEvent("timeless", null, null);
        when(eventMapper.selectList(any())).thenReturn(List.of(expired, upcoming, live, timeless));

        List<String> eventKeys = service.getActiveBroadcasts().stream()
                .map(event -> event.getEventKey())
                .toList();

        assertEquals(List.of("live", "timeless"), eventKeys);
        ArgumentCaptor<QueryWrapper<BroadcastEvent>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(eventMapper).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("start_at"));
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("end_at"));
    }

    @Test
    void contributionRejectsExpiredAndNotYetStartedActiveEvents() {
        LocalDateTime now = LocalDateTime.now();
        BroadcastEvent event = activeEvent("windowed", now.minusDays(1), now.minusSeconds(1));
        when(eventMapper.selectOne(any())).thenReturn(event);

        assertThrows(BusinessException.class, () -> service.contribute("windowed", PLAYER_ID, 1, "exploration"));

        event.setStartAt(LocalDateTime.now().plusMinutes(1));
        event.setEndAt(LocalDateTime.now().plusHours(1));
        assertThrows(BusinessException.class, () -> service.contribute("windowed", PLAYER_ID, 1, "exploration"));

        verify(contributionMapper, never()).selectOne(any());
        verify(contributionMapper, never()).insert(any(BroadcastContribution.class));
        verify(eventMapper, never()).update(any(), any());
    }

    @Test
    void activeBroadcastSummaryUsesTheSameStartAndEndWindow() {
        service.getBroadcastSummary();

        ArgumentCaptor<QueryWrapper<BroadcastEvent>> countQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(eventMapper).selectCount(countQuery.capture());
        assertTrue(countQuery.getValue().getSqlSegment().contains("start_at"));
        assertTrue(countQuery.getValue().getSqlSegment().contains("end_at"));
    }

    @Test
    void leaderboardFiltersToOneEventAndUsesStableTieOrdering() {
        Player firstPlayer = new Player();
        firstPlayer.setPlayerName("星海旅人");
        Player secondPlayer = new Player();
        secondPlayer.setPlayerName("夜航者");
        Player thirdPlayer = new Player();
        thirdPlayer.setPlayerName("星海回声");
        when(contributionMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(
                Map.of("player_id", 41L, "total_contribution", 12),
                Map.of("player_id", 52L, "total_contribution", 8),
                Map.of("player_id", 53L, "total_contribution", 8)));
        when(playerMapper.selectById(41L)).thenReturn(firstPlayer);
        when(playerMapper.selectById(52L)).thenReturn(secondPlayer);
        when(playerMapper.selectById(53L)).thenReturn(thirdPlayer);

        List<Map<String, Object>> result = service.getLeaderboard(EVENT_KEY, 10);

        assertEquals(3, result.size());
        assertEquals("星海旅人", result.get(0).get("player_name"));
        assertEquals(12L, result.get(0).get("total_contribution"));
        assertEquals(EVENT_KEY, result.get(0).get("event_key"));
        assertEquals(1, result.get(0).get("rank"));
        assertEquals(2, result.get(1).get("rank"));
        assertEquals(2, result.get(2).get("rank"), "同贡献者应并列，不按玩家 ID 分出先后名次");
        ArgumentCaptor<QueryWrapper<BroadcastContribution>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(contributionMapper).selectMaps(queryCaptor.capture());
        QueryWrapper<BroadcastContribution> query = queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("event_key"), query.getSqlSegment());
        assertTrue(query.getSqlSegment().contains("ORDER BY"));
    }

    @Test
    void playerLeaderboardPositionShowsRankAndMinimumPointsToOvertakeNearestHigherScore() {
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey(EVENT_KEY);
        when(eventMapper.selectOne(any())).thenReturn(event);
        BroadcastContribution mine = new BroadcastContribution();
        mine.setContributionValue(8);
        BroadcastContribution nextRank = new BroadcastContribution();
        nextRank.setContributionValue(12);
        when(contributionMapper.selectOne(any())).thenReturn(mine, nextRank);
        when(contributionMapper.selectCount(any())).thenReturn(3L, 1L);

        Map<String, Object> position = service.getPlayerLeaderboardPosition(EVENT_KEY, PLAYER_ID);

        assertEquals(true, position.get("participated"));
        assertEquals(3L, position.get("participantCount"));
        assertEquals(2L, position.get("rank"));
        assertEquals(8L, position.get("contribution"));
        assertEquals(5L, position.get("pointsToOvertakeNext"));
    }

    @Test
    void playerWithoutContributionGetsAnActionPromptStateInsteadOfAnArtificialRank() {
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey(EVENT_KEY);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(null);
        when(contributionMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> position = service.getPlayerLeaderboardPosition(EVENT_KEY, PLAYER_ID);

        assertEquals(false, position.get("participated"));
        assertNull(position.get("rank"));
        assertEquals(0L, position.get("contribution"));
        assertNull(position.get("pointsToOvertakeNext"));
        verify(contributionMapper).selectOne(any());
    }

    @Test
    void completedAiBroadcastMessageSummarizesObjectivesAndWorldlineEffects() {
        String message = completeAiBroadcastAndCaptureMessage(
                "{\"worldline_shift\":2,\"scenario_pressure\":-3}");

        assertTrue(message.contains("遗迹探索 2/2"), message);
        assertTrue(message.contains("世界线偏移 +2"), message);
        assertTrue(message.contains("剧本压力 -3"), message);
        assertFalse(message.contains("世界线发生轻微偏移"), message);
        verify(worldlineService).applyBroadcastWorldlineEffects("ai_test-event",
                "{\"worldline_shift\":2,\"scenario_pressure\":-3}");
    }

    @Test
    void completedAiBroadcastWithoutEffectsDoesNotClaimWorldlineChanged() {
        String message = completeAiBroadcastAndCaptureMessage(null);

        assertTrue(message.contains("遗迹探索 2/2"), message);
        assertTrue(message.contains("本次放送未配置直接世界线数值变化"), message);
        assertFalse(message.contains("世界线发生轻微偏移"), message);
        verify(worldlineService, never()).applyBroadcastWorldlineEffects(any(), any());
    }

    private String completeAiBroadcastAndCaptureMessage(String effectsJson) {
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey("ai_test-event");
        event.setTitle("遗迹联合调查");
        event.setStatus("active");
        event.setCurrentValue(0);
        event.setTargetValue(2);
        event.setMetadataJson("{\"objectives\":[{\"type\":\"explore_location\","
                + "\"label\":\"遗迹探索\",\"target\":2,\"current\":0}]}");
        event.setWorldlineEffectsJson(effectsJson);

        when(eventMapper.selectList(any())).thenReturn(List.of(event));
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(eventMapper.updateById(event)).thenReturn(1);
        when(contributionMapper.selectOne(any())).thenReturn(null);
        when(contributionMapper.insert(any(BroadcastContribution.class))).thenReturn(1);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(contributionMapper.selectCount(any())).thenReturn(0L);

        assertEquals(1, service.contributeToActiveAiEvents(PLAYER_ID, "explore_location", 2));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatService).saveSystemMessage(messageCaptor.capture(), any(), any());
        return messageCaptor.getValue();
    }

    @Test
    void claimRewardLocksPlayerAndRecordsCurrencyHeatAndItemRewards() throws Exception {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCoins(23);
        player.setStoryFragments(10);
        player.setStatsJson("{\"channelHeat\":2}");
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey(EVENT_KEY);
        event.setTitle("测试放送");
        event.setPersonalRewardsJson("{\"minContribution\":5,\"coins\":17,\"storyFragments\":3,"
                + "\"channelHeat\":7,\"items\":[{\"itemKey\":\"herb\",\"quantity\":2}]}");
        BroadcastContribution contribution = new BroadcastContribution();
        contribution.setContributionValue(5);
        contribution.setRewardClaimed(0);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(contribution);
        when(playerMapper.updateById(any(Player.class))).thenReturn(1);
        when(contributionMapper.updateById(any(BroadcastContribution.class))).thenReturn(1);
        when(playerEffectService.applyChannelHeatGainRate(PLAYER_ID, 7)).thenReturn(7);
        when(inventoryService.addItemWithResult(PLAYER_ID, "herb", 2))
                .thenReturn(new ItemGrantResult(2, 5, "灵草"));
        when(resourceTransactionService.record(any())).thenReturn(true);

        Map<String, Object> result = service.claimReward(EVENT_KEY, PLAYER_ID);

        assertEquals(true, result.get("claimed"));
        assertEquals(40, player.getCoins());
        assertEquals(13, player.getStoryFragments());
        assertEquals(9, new ObjectMapper().readTree(player.getStatsJson()).path("channelHeat").asInt());
        assertEquals(1, contribution.getRewardClaimed());
        verify(playerMapper).updateById(player);
        verify(contributionMapper).updateById(contribution);
        ArgumentCaptor<PlayerResourceTransactionService.Change> ledger =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService, org.mockito.Mockito.times(4)).record(ledger.capture());
        var byResource = ledger.getAllValues().stream().collect(java.util.stream.Collectors.toMap(
                change -> change.resourceType() + ":" + change.resourceKey(), change -> change));
        assertEquals(17L, byResource.get("currency:coins").amount());
        assertEquals(3L, byResource.get("currency:story_fragments").amount());
        assertEquals(7L, byResource.get("progress:channel_heat").amount());
        assertEquals(3L, byResource.get("item:herb").balanceBefore());
        assertEquals(5L, byResource.get("item:herb").balanceAfter());

        ArgumentCaptor<QueryWrapper<BroadcastContribution>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        InOrder lockOrder = inOrder(playerMapper, contributionMapper);
        lockOrder.verify(playerMapper).selectByIdForUpdate(PLAYER_ID);
        lockOrder.verify(contributionMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().trim().endsWith("FOR UPDATE"));
    }

    @Test
    void claimRewardRejectsAlreadyClaimedContributionWithoutGrantingAgain() {
        Player player = new Player();
        player.setCoins(23);
        BroadcastEvent event = new BroadcastEvent();
        event.setPersonalRewardsJson("{\"coins\":17}");
        BroadcastContribution contribution = new BroadcastContribution();
        contribution.setContributionValue(5);
        contribution.setRewardClaimed(1);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(contribution);

        assertThrows(BusinessException.class, () -> service.claimReward(EVENT_KEY, PLAYER_ID));

        verify(playerMapper, never()).updateById(any(Player.class));
        verify(contributionMapper, never()).updateById(any(BroadcastContribution.class));
    }

    @Test
    void claimRewardRejectsCurrencyOverflowWithoutSavingPlayerOrClaimMarker() {
        Player player = new Player();
        player.setCoins(Integer.MAX_VALUE);
        BroadcastEvent event = new BroadcastEvent();
        event.setPersonalRewardsJson("{\"coins\":17}");
        BroadcastContribution contribution = new BroadcastContribution();
        contribution.setContributionValue(5);
        contribution.setRewardClaimed(0);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(contribution);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claimReward(EVENT_KEY, PLAYER_ID));

        assertEquals(ErrorCode.BAD_REQUEST, error.getCode());
        assertEquals(Integer.MAX_VALUE, player.getCoins());
        assertEquals(0, contribution.getRewardClaimed());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(contributionMapper, never()).updateById(any(BroadcastContribution.class));
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void claimRewardReportsPlayerResourceWriteFailure() {
        Player player = new Player();
        player.setCoins(23);
        BroadcastEvent event = new BroadcastEvent();
        event.setPersonalRewardsJson("{\"coins\":17}");
        BroadcastContribution contribution = new BroadcastContribution();
        contribution.setContributionValue(5);
        contribution.setRewardClaimed(0);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(contribution);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.claimReward(EVENT_KEY, PLAYER_ID));

        verify(contributionMapper, never()).updateById(any(BroadcastContribution.class));
    }

    @Test
    void claimRewardReportsClaimMarkerWriteFailureInsteadOfReturningSuccess() {
        Player player = new Player();
        player.setCoins(23);
        BroadcastEvent event = new BroadcastEvent();
        event.setPersonalRewardsJson("{\"coins\":17}");
        BroadcastContribution contribution = new BroadcastContribution();
        contribution.setContributionValue(5);
        contribution.setRewardClaimed(0);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(contribution);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(contributionMapper.updateById(contribution)).thenReturn(0);
        when(resourceTransactionService.record(any())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.claimReward(EVENT_KEY, PLAYER_ID));
    }

    @Test
    void claimRewardDoesNotConsumeMarkerWhenAiRewardsAreOnlyStoredAsRewardTiers() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setCoins(23);
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey("ai_unwired_rewards");
        event.setRewardsJson("{\"participation\":{\"coins\":200},"
                + "\"completion\":{\"coins\":500},\"ranking\":{\"coins\":800}}");
        BroadcastContribution contribution = new BroadcastContribution();
        contribution.setContributionValue(5);
        contribution.setRewardClaimed(0);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(contributionMapper.selectOne(any())).thenReturn(contribution);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claimReward("ai_unwired_rewards", PLAYER_ID));

        assertEquals(ErrorCode.INVALID_FIELD_VALUE, error.getCode());
        assertEquals(0, contribution.getRewardClaimed());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(contributionMapper, never()).updateById(any(BroadcastContribution.class));
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void aiBroadcastContributionAdvancesOnlyMatchingObjectiveAndCapsAtItsTarget() throws Exception {
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey("ai_test_event");
        event.setStatus("active");
        event.setTargetValue(3);
        event.setCurrentValue(0);
        event.setMetadataJson("{\"objectives\":["
                + "{\"type\":\"explore_location\",\"target\":2,\"current\":0,\"score_per_unit\":4},"
                + "{\"type\":\"win_pk\",\"target\":1,\"current\":0,\"score_per_unit\":5}]}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(eventMapper.updateById(event)).thenReturn(1);
        when(contributionMapper.selectOne(any())).thenReturn(null);
        when(contributionMapper.insert(any(BroadcastContribution.class))).thenReturn(1);

        int advanced = service.contributeToActiveAiEvents(PLAYER_ID, "explore_location", 5);

        assertEquals(1, advanced);
        assertEquals(2, event.getCurrentValue());
        Map<String, Object> metadata = new ObjectMapper().readValue(event.getMetadataJson(), Map.class);
        var objectives = (java.util.List<Map<String, Object>>) metadata.get("objectives");
        assertEquals(2, objectives.get(0).get("current"));
        assertEquals(0, objectives.get(1).get("current"));

        ArgumentCaptor<BroadcastContribution> contribution =
                ArgumentCaptor.forClass(BroadcastContribution.class);
        verify(contributionMapper).insert(contribution.capture());
        assertEquals(8, contribution.getValue().getContributionValue());
        assertEquals("explore_location", contribution.getValue().getContributionType());

        ArgumentCaptor<QueryWrapper<BroadcastEvent>> lockQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(eventMapper).selectOne(lockQuery.capture());
        assertTrue(lockQuery.getValue().getSqlSegment().trim().endsWith("FOR UPDATE"));
    }

    @Test
    void createEventRejectsObjectivesWithoutImplementedGameplayActions() {
        Map<String, Object> draft = Map.of("title", "不可完成活动", "objectives", java.util.List.of(
                Map.of("type", "complete_stage", "target", 1, "label", "推进主线")));

        assertNull(service.createEvent(draft));

        verify(eventMapper, never()).insert(any(BroadcastEvent.class));
    }

    @Test
    void createEventInitializesSupportedObjectiveProgressAtZero() throws Exception {
        Map<String, Object> draft = Map.of("title", "探索活动", "durationMinutes", 30,
                "objectives", java.util.List.of(Map.of("type", "explore_location", "target", 12,
                        "label", "完成十二次探索", "score_per_unit", 2)));
        when(eventMapper.insert(any(BroadcastEvent.class))).thenReturn(1);

        String eventKey = service.createEvent(draft);

        assertTrue(eventKey.startsWith("ai_"));
        ArgumentCaptor<BroadcastEvent> savedEvent = ArgumentCaptor.forClass(BroadcastEvent.class);
        verify(eventMapper).insert(savedEvent.capture());
        assertEquals(12, savedEvent.getValue().getTargetValue());
        Map<String, Object> metadata = new ObjectMapper().readValue(savedEvent.getValue().getMetadataJson(), Map.class);
        var objectives = (java.util.List<Map<String, Object>>) metadata.get("objectives");
        assertEquals(0, objectives.get(0).get("current"));
    }

    private BroadcastEvent activeEvent(String eventKey, LocalDateTime startAt, LocalDateTime endAt) {
        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey(eventKey);
        event.setStatus("active");
        event.setTitle(eventKey);
        event.setTargetValue(10);
        event.setCurrentValue(0);
        event.setStartAt(startAt);
        event.setEndAt(endAt);
        return event;
    }
}
