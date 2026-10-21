package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerStoryLog;
import com.huazhenghai.readergame.mapper.ExplorationEventMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerStoryLogMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.vo.EquipmentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExploreServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private LocationMapper locationMapper;
    @Mock private ExplorationEventMapper eventMapper;
    @Mock private PlayerStoryLogMapper storyLogMapper;
    @Mock private PlayerTitleMapper playerTitleMapper;
    @Mock private RecoveryService recoveryService;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private TitleService titleService;
    @Mock private InventoryService inventoryService;
    @Mock private EquipmentService equipmentService;
    @Mock private BroadcastService broadcastService;
    @Mock private FactionService factionService;
    @Mock private QuestService questService;
    @Mock private SkillService skillService;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private DataSource dataSource;

    private ExploreServiceImpl exploreService;

    @BeforeEach
    void setUp() {
        exploreService = new ExploreServiceImpl(
                playerMapper, locationMapper, eventMapper, storyLogMapper, playerTitleMapper,
                recoveryService, playerLogService, resourceTransactionService,
                titleService, inventoryService, equipmentService,
                broadcastService, factionService, questService, skillService, new ObjectMapper(), dataSource,
                playerEffectService);
        // Mockito returns empty maps by default; a missing durable receipt must be null.
        lenient().when(playerLogService.findExplorationReceipt(any(), anyString())).thenReturn(null);
        lenient().when(playerLogService.findStoryChoiceReceipt(any(), anyString())).thenReturn(null);
        lenient().when(playerMapper.updateById(any(Player.class))).thenReturn(1);
        lenient().when(storyLogMapper.insert(any(PlayerStoryLog.class))).thenReturn(1);
    }

    private Player pendingPaidStoryPlayer(int coins, int storyFragments) {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(coins);
        player.setStoryFragments(storyFragments);
        player.setStatsJson("{\"exp\":0,\"level\":1,\"freePoints\":0,\"maxHp\":100}");
        player.setStoryFlagsJson("{}");
        player.setDecisionHistoryJson("[]");
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"paid_story\"],"
                + "\"pendingStorySnapshots\":{\"paid_story\":{\"instanceKey\":\"paid-story-instance\",\"rewards\":{}}}}");
        return player;
    }

    private ExplorationEvent paidStoryEvent(String rewardsJson) {
        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("paid_story");
        event.setEventType("story");
        event.setName("雾桥");
        event.setRepeatable(1);
        event.setChoicesJson("[{\"label\":\"支付通行费\",\"consequence_text\":\"雾桥让开了。\","
                + "\"rewards_override\":" + rewardsJson + "}]");
        return event;
    }

    @Test
    void explorationRequiresRequestKeyBeforeReadingOrChangingPlayerState() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(1L, "ruined_station", null, 9L));

        assertEquals("requestKey 不能为空", error.getMessage());
        verify(playerMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void directExplorationRejectsLocationWhenStoryPrerequisiteIsMissingEvenAtRequiredLevel() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStatsJson("{\"level\":12,\"stamina\":50}");
        player.setStageProgressJson("{}");
        player.setStoryFlagsJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(new LinkedHashMap<>(Map.of("level", 12)));

        Location lockedLocation = new Location();
        lockedLocation.setLocationKey("story_locked_location");
        lockedLocation.setUnlockConditionsJson(
                "{\"required_level\":10,\"prerequisite_events\":[\"route_witnessed\"]}");
        when(locationMapper.selectOne(any())).thenReturn(lockedLocation);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(1L, "story_locked_location", "locked-map-attempt", 9L));

        assertEquals("该地点尚未解锁", error.getMessage());
        verify(eventMapper, never()).selectList(any());
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void requiredRankEventUsesConfiguredRankProgressionOrder() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCurrentLocation("rank_test_location");
        player.setStatsJson("{\"level\":6,\"stamina\":30,\"avatarRank\":\"E\"}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(new LinkedHashMap<>(Map.of(
                "level", 6, "stamina", 30, "avatarRank", "E", "isResting", false)));

        Location location = new Location();
        location.setLocationKey("rank_test_location");
        location.setName("位阶门槛测试地点");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        ExplorationEvent dRankEvent = new ExplorationEvent();
        dRankEvent.setEventKey("d_rank_only_event");
        dRankEvent.setEventType("resource");
        dRankEvent.setName("D 级限定事件");
        dRankEvent.setWeight(1);
        dRankEvent.setStaminaCost(5);
        dRankEvent.setRepeatable(1);
        dRankEvent.setEnabled(1);
        dRankEvent.setRequiredConditionsJson("{\"required_rank\":\"D\"}");
        dRankEvent.setRewardsJson("{}");
        dRankEvent.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(List.of(dRankEvent));

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(1L, "rank_test_location", "rank-order-test", 9L));

        assertEquals("该地点暂无可用的探索事件", error.getMessage());
        verify(playerMapper, never()).updateById(player);
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void storyChoiceUnlocksAreScopedToEachPlayersRecordedDecisionAndSupportLegacyHistory() {
        Player choseUnlockingBranch = routePlayer(1L,
                "[{\"event_key\":\"branch_choice\",\"choice_index\":1}]");
        Player choseOtherBranch = routePlayer(2L,
                "[{\"event_key\":\"branch_choice\",\"choice_index\":0}]");
        Player hasNoDecision = routePlayer(3L, "[]");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(choseUnlockingBranch);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(choseOtherBranch);
        when(playerMapper.selectByIdForUpdate(3L)).thenReturn(hasNoDecision);
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                new LinkedHashMap<>(Map.of("stamina", 30, "level", 10, "avatarRank", "F")));
        when(skillService.calculateSkillBonus(any())).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(any())).thenReturn(Map.of());
        when(titleService.evaluateAndUnlockTitles(any(), eq(null)))
                .thenReturn(java.util.Collections.emptyList());

        Location location = new Location();
        location.setLocationKey("route_location");
        location.setName("路线测试地点");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent gatedStory = new ExplorationEvent();
        gatedStory.setEventKey("branch_target_story");
        gatedStory.setEventType("story");
        gatedStory.setName("分支后续");
        gatedStory.setDescription("只有选择对应路线的玩家才能遇到。");
        gatedStory.setWeight(1);
        gatedStory.setStaminaCost(1);
        gatedStory.setRepeatable(0);
        gatedStory.setEnabled(1);
        gatedStory.setRequiredConditionsJson(
                "{\"choice_unlockers\":[{\"event_key\":\"branch_choice\",\"choice_indices\":[1]}]}");
        gatedStory.setChoicesJson("[{\"label\":\"继续\"}]");
        gatedStory.setRewardsJson("{}");
        gatedStory.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(gatedStory));

        Map<String, Object> unlockedResult = exploreService.startExplore(
                1L, "route_location", "route-unlocked", 9L);
        assertEquals("branch_target_story", ((Map<?, ?>) unlockedResult.get("result")).get("event_key"),
                "已有旧格式决策历史的存档应继续获得对应分支");

        BusinessException wrongBranch = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(2L, "route_location", "route-wrong-choice", 9L));
        assertEquals("该地点暂无可用的探索事件", wrongBranch.getMessage());

        BusinessException noDecision = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(3L, "route_location", "route-no-choice", 9L));
        assertEquals("该地点暂无可用的探索事件", noDecision.getMessage());
        verify(playerMapper, never()).updateById(choseOtherBranch);
        verify(playerMapper, never()).updateById(hasNoDecision);
    }

    @Test
    void sideStoryEvidenceUnlocksResonanceOnlyOnTheReadersOwnTimeline() {
        Player missingDiary = routePlayer(4L,
                "[{\"event_key\":\"darkest_spring_forest_vision\",\"choice_index\":0}]");
        missingDiary.setStageProgressJson("{\"storyEventsTriggered\":["
                + "\"darkest_spring_first_omen\",\"darkest_spring_forest_vision\"]}");

        Player foundDiary = routePlayer(5L,
                "[{\"event_key\":\"darkest_spring_forest_vision\",\"choice_index\":0}]");
        foundDiary.setStageProgressJson("{\"storyEventsTriggered\":["
                + "\"darkest_spring_first_omen\",\"darkest_spring_forest_vision\"],"
                + "\"sideEventsTriggered\":[\"geumho_echo_whisper\"]}");

        when(playerMapper.selectByIdForUpdate(4L)).thenReturn(missingDiary);
        when(playerMapper.selectByIdForUpdate(5L)).thenReturn(foundDiary);
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                new LinkedHashMap<>(Map.of("stamina", 30, "level", 10, "avatarRank", "F", "isResting", false)));
        when(skillService.calculateSkillBonus(any())).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(any())).thenReturn(Map.of());
        when(titleService.evaluateAndUnlockTitles(any(), eq(null))).thenReturn(List.of());

        Location location = new Location();
        location.setLocationKey("route_location");
        location.setName("路线测试地点");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        ExplorationEvent resonance = new ExplorationEvent();
        resonance.setEventKey("darkest_spring_story_stitch");
        resonance.setEventType("story");
        resonance.setName("交错的两段记录");
        resonance.setWeight(1);
        resonance.setStaminaCost(0);
        resonance.setRepeatable(0);
        resonance.setEnabled(1);
        resonance.setRequiredConditionsJson("{\"required_events\":["
                + "\"darkest_spring_first_omen\",\"darkest_spring_forest_vision\"],"
                + "\"required_side_events\":[\"geumho_echo_whisper\"],"
                + "\"choice_unlockers\":[{\"event_key\":\"darkest_spring_forest_vision\","
                + "\"choice_indices\":[0,1]}]}");
        resonance.setChoicesJson("[{\"label\":\"把两段记录写在同一页\","
                + "\"set_story_flags\":{\"story_dark_spring_interwoven\":true}}]");
        resonance.setRewardsJson("{}");
        resonance.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(List.of(resonance));

        BusinessException missingEvidence = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(4L, "route_location", "resonance-without-diary", 9L));
        assertEquals("该地点暂无可用的探索事件", missingEvidence.getMessage());

        Map<String, Object> unlocked = exploreService.startExplore(
                5L, "route_location", "resonance-with-diary", 9L);
        assertEquals("darkest_spring_story_stitch", ((Map<?, ?>) unlocked.get("result")).get("event_key"));
        verify(playerMapper, never()).updateById(missingDiary);
    }

    @Test
    void branchRuledOutByRecordedChoiceDoesNotBlockHigherLocationStories() {
        Map<String, Object> result = exploreAcrossLowerLocationBranch(0);

        assertEquals(false, result.get("stories_blocked"));
        assertEquals(java.util.Collections.emptyList(), result.get("lower_unfinished"));
        assertEquals(true, result.get("stories_exhausted"),
                "a permanently unavailable branch should not keep the location marked unfinished");
        assertEquals("available_resource", ((Map<?, ?>) result.get("result")).get("event_key"));
    }

    @Test
    void unfinishedLowerStoryStillBlocksHigherLocationStoriesWhenChosenBranchUnlocksIt() {
        Map<String, Object> result = exploreAcrossLowerLocationBranch(1);

        assertEquals(true, result.get("stories_blocked"));
        assertEquals(java.util.List.of("忠武路(1)"), result.get("lower_unfinished"));
        assertEquals(false, result.get("stories_exhausted"));
        assertEquals("available_resource", ((Map<?, ?>) result.get("result")).get("event_key"),
                "blocking stories must still allow non-story exploration");
    }

    @Test
    void unresolvedLowerStoryChoiceStillBlocksUntilItsRouteIsKnown() {
        Map<String, Object> result = exploreAcrossLowerLocationBranch(null);

        assertEquals(true, result.get("stories_blocked"));
        assertEquals(java.util.List.of("忠武路(1)"), result.get("lower_unfinished"));
        assertEquals(false, result.get("stories_exhausted"));
    }

    private Map<String, Object> exploreAcrossLowerLocationBranch(Integer selectedChoice) {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("分支测试读者");
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setStatsJson("{\"stamina\":30,\"level\":10,\"avatarRank\":\"F\"}");
        player.setStageProgressJson(selectedChoice == null ? "{}"
                : "{\"storyEventsTriggered\":[\"chungmuro_iron_king_story\"]}");
        player.setStoryFlagsJson("{}");
        player.setDecisionHistoryJson(selectedChoice == null ? "[]"
                : "[{\"event_key\":\"chungmuro_iron_king_story\","
                + "\"choice_index\":" + selectedChoice + "}]");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(new LinkedHashMap<>(Map.of(
                "stamina", 30, "level", 10, "avatarRank", "F", "isResting", false)));
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of());
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.Collections.emptyList());

        Location currentLocation = new Location();
        currentLocation.setLocationKey("myeongdong");
        currentLocation.setName("明洞");
        currentLocation.setMinLevel(6);
        currentLocation.setUnlockConditionsJson("{}");
        currentLocation.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(currentLocation);

        Location lowerLocation = new Location();
        lowerLocation.setLocationKey("chungmuro");
        lowerLocation.setName("忠武路");
        lowerLocation.setMinLevel(5);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of(lowerLocation));

        ExplorationEvent lowerBranch = new ExplorationEvent();
        lowerBranch.setEventKey("chungmuro_underground_resistance");
        lowerBranch.setEventType("story");
        lowerBranch.setEnabled(1);
        lowerBranch.setRepeatable(0);
        lowerBranch.setRequiredConditionsJson("{\"choice_unlockers\":["
                + "{\"event_key\":\"chungmuro_iron_king_story\",\"choice_indices\":[1]}]}");

        ExplorationEvent currentBranch = new ExplorationEvent();
        currentBranch.setEventKey("myeongdong_library_story");
        currentBranch.setEventType("story");
        currentBranch.setEnabled(1);
        currentBranch.setRepeatable(0);
        currentBranch.setRequiredConditionsJson("{\"choice_unlockers\":["
                + "{\"event_key\":\"chungmuro_iron_king_story\",\"choice_indices\":[1]}]}");

        ExplorationEvent resourceEvent = new ExplorationEvent();
        resourceEvent.setEventKey("available_resource");
        resourceEvent.setEventType("resource");
        resourceEvent.setName("可用补给");
        resourceEvent.setWeight(1);
        resourceEvent.setStaminaCost(1);
        resourceEvent.setRepeatable(1);
        resourceEvent.setEnabled(1);
        resourceEvent.setRequiredConditionsJson("{}");
        resourceEvent.setRewardsJson("{}");
        resourceEvent.setProgressEffectsJson("{}");

        when(eventMapper.selectList(any())).thenReturn(
                java.util.List.of(lowerBranch), java.util.List.of(currentBranch, resourceEvent));

        return exploreService.startExplore(1L, "myeongdong", "lower-branch-" + selectedChoice, 9L);
    }

    private Player routePlayer(Long playerId, String decisionHistoryJson) {
        Player player = new Player();
        player.setId(playerId);
        player.setUserId(9L);
        player.setPlayerName("测试读者" + playerId);
        player.setCurrentLocation("route_location");
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setStatsJson("{\"stamina\":30,\"level\":10,\"avatarRank\":\"F\"}");
        player.setStageProgressJson("{}");
        player.setStoryFlagsJson("{}");
        player.setDecisionHistoryJson(decisionHistoryJson);
        return player;
    }

    @Test
    void storyChoiceRequiresRequestKeyBeforeReadingOrChangingPlayerState() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(1L, "choice_reward", 0, null, "story-instance", 9L));

        assertEquals("requestKey 不能为空", error.getMessage());
        verify(playerMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void storyChoiceRejectsInsufficientCoinsBeforeSavingAnyChoiceState() {
        Player player = pendingPaidStoryPlayer(49, 10);
        ExplorationEvent event = paidStoryEvent("{\"coins\":-50,\"storyFragments\":-2}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-coins-short", "paid-story-instance", 9L));

        assertEquals("星币不足，无法选择该选项（需要 50，当前 49）", error.getMessage());
        assertEquals(49, player.getCoins());
        assertEquals(10, player.getStoryFragments());
        assertTrue(player.getStageProgressJson().contains("\"paid-story-instance\""));
        assertEquals("[]", player.getDecisionHistoryJson());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void storyChoiceRejectsInsufficientStoryFragmentsBeforeSavingAnyChoiceState() {
        Player player = pendingPaidStoryPlayer(100, 1);
        ExplorationEvent event = paidStoryEvent("{\"coins\":-50,\"storyFragments\":-2}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-fragments-short", "paid-story-instance", 9L));

        assertEquals("故事碎片不足，无法选择该选项（需要 2，当前 1）", error.getMessage());
        assertEquals(100, player.getCoins());
        assertEquals(1, player.getStoryFragments());
        assertTrue(player.getStageProgressJson().contains("\"paid-story-instance\""));
        assertEquals("[]", player.getDecisionHistoryJson());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void storyChoiceRejectsCoinOverflowBeforeSavingAnyChoiceState() {
        Player player = pendingPaidStoryPlayer(Integer.MAX_VALUE, 10);
        ExplorationEvent event = paidStoryEvent("{\"coins\":1}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-coins-overflow", "paid-story-instance", 9L));

        assertEquals("星币已达到可保存上限，暂时无法领取", error.getMessage());
        assertEquals(Integer.MAX_VALUE, player.getCoins());
        assertEquals(10, player.getStoryFragments());
        assertEquals("[]", player.getDecisionHistoryJson());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
    }

    @Test
    void storyChoiceRejectsStoryFragmentOverflowBeforeSavingAnyChoiceState() {
        Player player = pendingPaidStoryPlayer(100, Integer.MAX_VALUE);
        ExplorationEvent event = paidStoryEvent("{\"storyFragments\":1}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-fragments-overflow", "paid-story-instance", 9L));

        assertEquals("故事碎片已达到可保存上限，暂时无法领取", error.getMessage());
        assertEquals(100, player.getCoins());
        assertEquals(Integer.MAX_VALUE, player.getStoryFragments());
        assertEquals("[]", player.getDecisionHistoryJson());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
    }

    @Test
    void storyChoiceRejectsChannelHeatOverflowBeforeSavingAnyChoiceState() {
        Player player = pendingPaidStoryPlayer(100, 10);
        player.setStatsJson("{\"channelHeat\":" + Integer.MAX_VALUE + "}");
        ExplorationEvent event = paidStoryEvent("{\"channelHeat\":1}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(playerEffectService.applyChannelHeatGainRate(1L, 1)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-channel-heat-overflow", "paid-story-instance", 9L));

        assertEquals("星流热度超出可保存范围，操作无法完成", error.getMessage());
        assertEquals(100, player.getCoins());
        assertEquals(10, player.getStoryFragments());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
    }

    @Test
    void storyChoiceRejectsWorldLineShiftOverflowAtEitherBoundary() {
        Player upper = pendingPaidStoryPlayer(100, 10);
        upper.setStatsJson("{\"worldLineShift\":" + Integer.MAX_VALUE + "}");
        ExplorationEvent positiveEvent = paidStoryEvent("{\"worldLineShift\":1}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(upper);
        when(eventMapper.selectOne(any())).thenReturn(positiveEvent);

        BusinessException upperError = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-shift-upper-overflow", "paid-story-instance", 9L));

        Player lower = pendingPaidStoryPlayer(100, 10);
        lower.setStatsJson("{\"worldLineShift\":" + Integer.MIN_VALUE + "}");
        ExplorationEvent negativeEvent = paidStoryEvent("{\"worldLineShift\":-1}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(lower);
        when(eventMapper.selectOne(any())).thenReturn(negativeEvent);

        BusinessException lowerError = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "paid_story", 0, "paid-story-shift-lower-overflow", "paid-story-instance", 9L));

        assertEquals("世界线偏移超出可保存范围，操作无法完成", upperError.getMessage());
        assertEquals(upperError.getMessage(), lowerError.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
    }

    @Test
    void storyChoiceConsumesBothResourcesOnceAndReplaysTheReceiptWithoutChargingAgain() {
        Player player = pendingPaidStoryPlayer(50, 2);
        ExplorationEvent event = paidStoryEvent("{\"coins\":-50,\"storyFragments\":-2}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.Collections.emptyList());

        var result = exploreService.makeChoice(
                1L, "paid_story", 0, "paid-story-consume-once", "paid-story-instance", 9L);

        assertEquals(0, player.getCoins());
        assertEquals(0, player.getStoryFragments());
        assertEquals(-50, ((Map<?, ?>) result.get("rewards")).get("coins"));
        assertEquals(-2, ((Map<?, ?>) result.get("rewards")).get("storyFragments"));

        org.mockito.ArgumentCaptor<Map<String, Object>> receiptCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(
                eq(1L), eq("story"), eq("你选择了: 支付通行费"), receiptCaptor.capture());
        when(playerLogService.findStoryChoiceReceipt(1L, "paid-story-consume-once"))
                .thenReturn(receiptCaptor.getValue());

        var replay = exploreService.makeChoice(
                1L, "paid_story", 0, "paid-story-consume-once", "paid-story-instance", 9L);

        assertEquals(true, replay.get("request_replayed"));
        assertEquals(0, player.getCoins());
        assertEquals(0, player.getStoryFragments());
        verify(playerMapper, times(2)).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        verify(playerLogService, times(1))
                .addLog(eq(1L), eq("story"), eq("你选择了: 支付通行费"), anyMap());
    }

    @Test
    void explorationSeedFillsMissingRowsWithoutDeletingExistingConfiguration() {
        when(eventMapper.selectCount(null)).thenReturn(1L);

        exploreService.seedExplorationData();

        verify(eventMapper, never()).delete(any());
        verify(locationMapper, never()).delete(any());
    }

    @Test
    void explorationSeedPersistsChoiceUnlockConditions() throws Exception {
        when(eventMapper.selectCount(null)).thenReturn(0L);

        exploreService.seedExplorationData();

        org.mockito.ArgumentCaptor<ExplorationEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(ExplorationEvent.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce()).insert(eventCaptor.capture());
        ExplorationEvent gatedStory = eventCaptor.getAllValues().stream()
                .filter(event -> "geumho_underground_story".equals(event.getEventKey()))
                .findFirst().orElseThrow();
        JsonNode conditions = new ObjectMapper().readTree(gatedStory.getRequiredConditionsJson());
        assertEquals("geumho_shelter_story",
                conditions.path("choice_unlockers").get(0).path("event_key").asText());
        assertEquals(1,
                conditions.path("choice_unlockers").get(0).path("choice_indices").get(0).asInt());
    }

    @Test
    void explorationSeedPersistsMonsterKeysFromEveryBattleEncounter() throws Exception {
        when(eventMapper.selectCount(null)).thenReturn(0L);

        exploreService.seedExplorationData();

        org.mockito.ArgumentCaptor<ExplorationEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(ExplorationEvent.class);
        verify(eventMapper, org.mockito.Mockito.atLeastOnce()).insert(eventCaptor.capture());
        Map<String, ExplorationEvent> persistedByKey = new LinkedHashMap<>();
        for (ExplorationEvent event : eventCaptor.getAllValues()) {
            persistedByKey.put(event.getEventKey(), event);
        }

        int battleCount = 0;
        try (InputStream explorationStream = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/seed_explore.json"))) {
            JsonNode seedEntries = new ObjectMapper().readTree(explorationStream);
            for (JsonNode entry : seedEntries) {
                if (!"event".equals(entry.path("type").asText())
                        || !"battle_placeholder".equals(entry.path("event_type").asText())) {
                    continue;
                }

                battleCount++;
                String eventKey = entry.path("event_key").asText();
                ExplorationEvent persisted = persistedByKey.get(eventKey);
                assertTrue(persisted != null, "战斗事件未写入种子：" + eventKey);
                JsonNode expectedRisks = entry.has("risks")
                        ? entry.path("risks") : entry.path("risks_json");
                JsonNode actualRisks = new ObjectMapper().readTree(persisted.getRisksJson());
                assertEquals(expectedRisks.path("monsterKey").asText(),
                        actualRisks.path("monsterKey").asText(),
                        "战斗事件的怪物键未持久化：" + eventKey);
            }
        }

        assertEquals(18, battleCount, "探索种子中的战斗遭遇数量发生变化，请同步审计");
    }

    @Test
    void flameGiantStoryRewardReferencesSeededStarfallEquipment() throws Exception {
        try (InputStream explorationStream = Objects.requireNonNull(
                     getClass().getResourceAsStream("/db/seed_explore.json"));
             InputStream equipmentStream = Objects.requireNonNull(
                     getClass().getResourceAsStream("/db/seed_equipment.sql"))) {
            JsonNode events = new ObjectMapper().readTree(explorationStream);
            JsonNode flameGiantStory = null;
            for (JsonNode event : events) {
                if ("dongdaemun_flame_giant_story".equals(event.path("event_key").asText())) {
                    flameGiantStory = event;
                    break;
                }
            }

            assertTrue(flameGiantStory != null, "火焰巨人剧情必须存在于探索种子数据");
            String rewardKey = flameGiantStory.path("choices").get(1)
                    .path("rewards_override").path("equipment").get(0)
                    .path("equipmentKey").asText();
            String equipmentSeed = new String(equipmentStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("starfall_blade", rewardKey);
            assertTrue(equipmentSeed.contains("('" + rewardKey + "',"),
                    "剧情奖励必须引用已定义的装备: " + rewardKey);
        }
    }

    @Test
    void geumhoInformationChoiceChargesItsStatedStoryFragmentCostInBothSeedsAndMigration() throws Exception {
        JsonNode events;
        String sqlSeed;
        String migration;
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream explorationStream = Objects.requireNonNull(
                     getClass().getResourceAsStream("/db/seed_explore.json"));
             InputStream sqlSeedStream = Objects.requireNonNull(
                     getClass().getResourceAsStream("/db/seed_explore.sql"));
             InputStream migrationStream = Objects.requireNonNull(getClass().getResourceAsStream(
                     "/db/migrations/20260925_charge_geumho_info_fragment.sql"))) {
            events = mapper.readTree(explorationStream);
            sqlSeed = new String(sqlSeedStream.readAllBytes(), StandardCharsets.UTF_8);
            migration = new String(migrationStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        JsonNode undergroundStory = null;
        for (JsonNode event : events) {
            if ("geumho_underground_story".equals(event.path("event_key").asText())) {
                undergroundStory = event;
                break;
            }
        }

        assertTrue(undergroundStory != null, "金湖站地下交易剧情必须存在于 JSON 种子");
        JsonNode firstChoice = undergroundStory.path("choices").get(0);
        assertTrue(firstChoice.path("label").asText().contains("消耗1碎片"));
        assertEquals(-1, firstChoice.path("rewards_override").path("storyFragments").asInt());
        assertTrue(sqlSeed.contains("\"storyFragments\":-1"), "SQL 种子必须同步扣除 1 个故事碎片");
        assertTrue(migration.contains("geumho_underground_story"));
        assertTrue(migration.contains("$[0].rewards_override.storyFragments"));
    }

    @Test
    void everyExplorationItemRewardHasASeedDefinitionAndUpgradeMigration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode events;
        try (InputStream explorationStream = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/seed_explore.json"))) {
            events = mapper.readTree(explorationStream);
        }

        Set<String> referencedItemKeys = new HashSet<>();
        for (JsonNode event : events) {
            for (JsonNode item : event.path("rewards").path("items")) {
                if (item.path("itemKey").isTextual()) referencedItemKeys.add(item.path("itemKey").asText());
            }
            for (JsonNode choice : event.path("choices")) {
                for (JsonNode item : choice.path("rewards_override").path("items")) {
                    if (item.path("itemKey").isTextual()) referencedItemKeys.add(item.path("itemKey").asText());
                }
            }
        }

        Pattern itemRow = Pattern.compile("(?m)^\\s*\\('([^']+)'\\s*,");
        Set<String> allSeededItemKeys = new HashSet<>();
        for (String file : List.of("seed_items.sql", "seed_narrative_items.sql", "seed_synthesis_items.sql",
                "seed_exploration_reward_items.sql")) {
            String sql;
            try (InputStream seedStream = Objects.requireNonNull(getClass().getResourceAsStream("/db/" + file))) {
                sql = new String(seedStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher matcher = itemRow.matcher(sql);
            while (matcher.find()) allSeededItemKeys.add(matcher.group(1));
        }

        Set<String> missingDefinitions = new HashSet<>(referencedItemKeys);
        missingDefinitions.removeAll(allSeededItemKeys);
        assertTrue(missingDefinitions.isEmpty(), "探索奖励引用了未定义的物品: " + missingDefinitions);

        Set<String> explorationSeedKeys = new HashSet<>();
        try (InputStream seedStream = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/seed_exploration_reward_items.sql"))) {
            Matcher matcher = itemRow.matcher(new String(seedStream.readAllBytes(), StandardCharsets.UTF_8));
            while (matcher.find()) explorationSeedKeys.add(matcher.group(1));
        }
        Set<String> migrationKeys = new HashSet<>();
        try (InputStream migrationStream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/db/migrations/20260925_add_exploration_reward_items.sql"))) {
            Matcher matcher = itemRow.matcher(new String(migrationStream.readAllBytes(), StandardCharsets.UTF_8));
            while (matcher.find()) migrationKeys.add(matcher.group(1));
        }
        assertEquals(explorationSeedKeys, migrationKeys, "新库种子与既有数据库迁移必须包含相同物品键");
    }

    @Test
    void completedOneTimeStoryCannotGrantChoiceRewardsAgain() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"station_first_trace\"],\"routeHistory\":[\"station_first_trace\"]}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("station_first_trace");
        event.setName("第一道痕迹");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"留下记录\",\"rewards_override\":{\"coins\":500}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(1L, "station_first_trace", 0,
                        "completed-story-choice", 9L));

        assertEquals("该剧情已经完成，不能重复选择", error.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void pendingStoryCanBeRestoredWithItsActualRewardSnapshot() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"station_first_trace\",\"already_done\"],"
                + "\"routeHistory\":[\"already_done\"],\"pendingStorySnapshots\":{"
                + "\"station_first_trace\":{\"instanceKey\":\"pending-instance-1\",\"rewards\":{\"coins\":12},"
                + "\"new_titles\":[{\"name\":\"守望者\"}],\"personal_contexts\":[\"当时的病房记忆\"]}}}");
        when(playerMapper.selectById(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("station_first_trace");
        event.setEventType("story");
        event.setName("第一道痕迹");
        event.setDescription("轨道边有一本湿透的笔记。");
        event.setChoicesJson("[{\"label\":\"留下记录\"}]");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));

        var pending = exploreService.getPendingStories(1L, 9L);

        assertEquals(1, pending.size());
        assertEquals("station_first_trace", ((java.util.Map<?, ?>) pending.get(0).get("result")).get("event_key"));
        assertEquals("pending-instance-1", pending.get(0).get("story_instance_key"));
        assertEquals(12, ((java.util.Map<?, ?>) pending.get(0).get("rewards")).get("coins"));
        assertEquals("守望者", ((java.util.Map<?, ?>) ((java.util.List<?>) pending.get(0).get("new_titles")).get(0)).get("name"));
        assertEquals(java.util.List.of("当时的病房记忆"), ((java.util.Map<?, ?>) pending.get(0)
                .get("result")).get("personal_contexts"));
    }

    @Test
    void finalePersonalContextsAreResolvedFromEachPlayersOwnNpcFlags() {
        Player withLocationMemory = new Player();
        withLocationMemory.setId(11L);
        withLocationMemory.setUserId(9L);
        withLocationMemory.setStoryFlagsJson("{\"oldest_dream_location\":true}");
        withLocationMemory.setStageProgressJson("{\"storyEventsTriggered\":[\"ending_dream_continuation_choice\"]}");

        Player withLastPageMemory = new Player();
        withLastPageMemory.setId(22L);
        withLastPageMemory.setUserId(9L);
        withLastPageMemory.setStoryFlagsJson("{\"oldest_dream_last_page\":true}");
        withLastPageMemory.setStageProgressJson("{\"storyEventsTriggered\":[\"ending_dream_continuation_choice\"]}");
        when(playerMapper.selectById(11L)).thenReturn(withLocationMemory);
        when(playerMapper.selectById(22L)).thenReturn(withLastPageMemory);

        ExplorationEvent finale = new ExplorationEvent();
        finale.setEventKey("ending_dream_continuation_choice");
        finale.setEventType("story");
        finale.setName("是否延续梦境");
        finale.setDescription("作出你的选择。");
        finale.setRepeatable(0);
        finale.setRequiredConditionsJson("{\"personal_contexts\":["
                + "{\"story_flag\":\"oldest_dream_location\",\"text\":\"病房与少年\"},"
                + "{\"story_flag\":\"oldest_dream_last_page\",\"text\":\"尚未写完的最后一页\"}]}");
        finale.setChoicesJson("[{\"label\":\"继续\"}]");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(finale));

        var firstPlayerPending = exploreService.getPendingStories(11L, 9L);
        var secondPlayerPending = exploreService.getPendingStories(22L, 9L);

        assertEquals(java.util.List.of("病房与少年"), ((java.util.Map<?, ?>) firstPlayerPending.get(0)
                .get("result")).get("personal_contexts"));
        assertEquals(java.util.List.of("尚未写完的最后一页"), ((java.util.Map<?, ?>) secondPlayerPending.get(0)
                .get("result")).get("personal_contexts"));
    }

    @Test
    void pendingBattleCanBeRestoredOnlyForItsOwner() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectById(1L)).thenReturn(player);
        Map<String, Object> battle = Map.of(
                "result_type", "battle",
                "request_key", "explore-request-1",
                "monster_key", "station_rat",
                "result", Map.of("event_key", "station_rat_event", "event_type", "battle",
                        "name", "变异站鼠", "description", "一只站鼠挡住去路。")
        );
        when(playerLogService.findPendingBattleEncounters(1L)).thenReturn(java.util.List.of(battle));

        var pending = exploreService.getPendingBattles(1L, 9L);

        assertEquals(java.util.List.of(battle), pending);
        verify(playerLogService).findPendingBattleEncounters(1L);
        assertThrows(BusinessException.class, () -> exploreService.getPendingBattles(1L, 10L));
        verify(playerLogService, times(1)).findPendingBattleEncounters(1L);
    }

    @Test
    void pendingBattleAcknowledgementValidatesKeyAndPlayerOwnership() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectById(1L)).thenReturn(player);

        exploreService.acknowledgePendingBattle(1L, "explore-request-1", 9L);

        verify(playerLogService).acknowledgeBattleEncounter(1L, "explore-request-1");
        assertThrows(BusinessException.class,
                () -> exploreService.acknowledgePendingBattle(1L, " ", 9L));
        assertThrows(BusinessException.class,
                () -> exploreService.acknowledgePendingBattle(1L, "explore-request-2", 10L));
        verify(playerLogService, times(1)).acknowledgeBattleEncounter(any(), anyString());
    }

    @Test
    void legacyOneTimeStoryWithoutSnapshotCanBeRestoredWithoutRegrantingRewards() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"legacy_pending\"],\"routeHistory\":[]}");
        when(playerMapper.selectById(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("legacy_pending");
        event.setEventType("story");
        event.setName("旧存档待选剧情");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"继续前进\"}]");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));

        var pending = exploreService.getPendingStories(1L, 9L);

        assertEquals(1, pending.size());
        assertEquals(java.util.Collections.emptyMap(), pending.get(0).get("rewards"));
        assertEquals(java.util.Collections.emptyList(), pending.get(0).get("new_titles"));
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void triggeredRepeatableStoryWithoutPendingSnapshotIsNotRestoredAgain() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"repeatable_story\"]}");
        when(playerMapper.selectById(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("repeatable_story");
        event.setEventType("story");
        event.setRepeatable(1);
        event.setChoicesJson("[{\"label\":\"看看\"}]");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));

        var pending = exploreService.getPendingStories(1L, 9L);

        assertEquals(java.util.Collections.emptyList(), pending);
    }

    @Test
    void repeatedExploreRequestReplaysOriginalResultWithoutRunningExplorationAgain() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("测试读者");
        player.setCurrentLocation("ruined_station");
        player.setCoins(28);
        player.setStatsJson("{\"hp\":80,\"stamina\":41}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        Map<String, Object> storedResponse = new LinkedHashMap<>();
        storedResponse.put("result_type", "resource");
        storedResponse.put("stamina_cost_paid", 5);
        storedResponse.put("result", Map.of("event_key", "cached_supply", "event_type", "resource"));
        storedResponse.put("rewards", Map.of("coins", 15));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("requestLocationKey", "ruined_station");
        receipt.put("requestResponse", storedResponse);
        when(playerLogService.findExplorationReceipt(1L, "retry-1")).thenReturn(receipt);
        when(locationMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> replay = exploreService.startExplore(
                1L, "ruined_station", "retry-1", 9L);

        assertEquals(true, replay.get("request_replayed"));
        assertEquals("retry-1", replay.get("request_key"));
        assertEquals(15, ((Map<?, ?>) replay.get("rewards")).get("coins"));
        Map<?, ?> snapshot = (Map<?, ?>) replay.get("player");
        Map<?, ?> replayStats = (Map<?, ?>) snapshot.get("stats");
        assertEquals(41, ((Number) replayStats.get("stamina")).intValue());
        verify(recoveryService, never()).applyRecovery(any(Player.class));
        verify(eventMapper, never()).selectList(any());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void exploreRequestKeyCannotBeReusedForAnotherLocation() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerLogService.findExplorationReceipt(1L, "retry-1")).thenReturn(Map.of(
                "requestLocationKey", "ruined_station",
                "requestResponse", Map.of("result_type", "resource")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(1L, "quiet_archive", "retry-1", 9L));

        assertTrue(error.getMessage().contains("其他探索地点"));
        verify(recoveryService, never()).applyRecovery(any(Player.class));
        verify(eventMapper, never()).selectList(any());
    }

    @Test
    void battleEncounterExploreRequestAlsoStoresReplayReceipt() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("测试读者");
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("hp", 100);
        stats.put("stamina", 30);
        stats.put("level", 1);
        stats.put("isResting", false);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of());

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent encounter = new ExplorationEvent();
        encounter.setEventKey("station_rat_encounter");
        encounter.setEventType("battle_placeholder");
        encounter.setName("变异站鼠");
        encounter.setDescription("铁轨间传来窸窣声。");
        encounter.setWeight(1);
        encounter.setStaminaCost(5);
        encounter.setRepeatable(1);
        encounter.setEnabled(1);
        encounter.setRequiredConditionsJson("{}");
        encounter.setRisksJson("{\"monsterKey\":\"station_rat\"}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(encounter));

        Map<String, Object> response = exploreService.startExplore(
                1L, "ruined_station", "battle-retry-1", 9L);

        assertEquals("battle-retry-1", response.get("request_key"));
        assertEquals("ruined_station", response.get("location_key"));
        assertEquals("废弃车站", response.get("location_name"));
        assertFalse((Boolean) response.get("request_replayed"));
        assertEquals(5, response.get("stamina_cost_paid"));
        assertEquals(25, stats.get("stamina"));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> metadata =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("battle"),
                eq("变异站鼠（遭遇，消耗体力5）"), metadata.capture());
        assertEquals("battle-retry-1", metadata.getValue().get("requestKey"));
        assertEquals(5, metadata.getValue().get("staminaCost"));
        assertTrue(metadata.getValue().containsKey("requestResponse"));
        verify(factionService).contribute(1L, null, "explore", 1L,
                "explore", "station_rat_encounter", null);
        verify(questService).addProgress(1L, "explore_count", 1,
                "explore", "station_rat_encounter");
        verify(playerMapper).updateById(player);

        when(playerLogService.findExplorationReceipt(1L, "battle-retry-1")).thenReturn(Map.of(
                "requestLocationKey", "ruined_station",
                "requestResponse", Map.of(
                        "result_type", "battle",
                        "stamina_cost_paid", 5,
                        "result", Map.of("event_key", "station_rat_encounter"),
                        "request_key", "battle-retry-1")));
        Map<String, Object> replay = exploreService.startExplore(
                1L, "ruined_station", "battle-retry-1", 9L);

        assertEquals(true, replay.get("request_replayed"));
        assertEquals(5, replay.get("stamina_cost_paid"));
        assertEquals(25, stats.get("stamina"));
        verify(factionService, times(1)).contribute(1L, null, "explore", 1L,
                "explore", "station_rat_encounter", null);
        verify(questService, times(1)).addProgress(1L, "explore_count", 1,
                "explore", "station_rat_encounter");
        verify(playerMapper, times(1)).updateById(player);
    }

    @Test
    void battleEncounterDoesNotReturnWhenPlayerProgressWasNotSaved() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("测试读者");
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("hp", 100);
        stats.put("stamina", 30);
        stats.put("level", 1);
        stats.put("isResting", false);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of());
        when(playerMapper.updateById(player)).thenReturn(0);

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent encounter = new ExplorationEvent();
        encounter.setEventKey("station_rat_encounter");
        encounter.setEventType("battle_placeholder");
        encounter.setName("变异站鼠");
        encounter.setWeight(1);
        encounter.setRepeatable(1);
        encounter.setEnabled(1);
        encounter.setRequiredConditionsJson("{}");
        encounter.setRisksJson("{\"monsterKey\":\"station_rat\"}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(encounter));

        assertThrows(IllegalStateException.class, () -> exploreService.startExplore(
                1L, "ruined_station", "battle-save-failure", 9L));

        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void battleEncounterRequiresStaminaBeforeReturningEncounter() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("测试读者");
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("hp", 100);
        stats.put("stamina", 3);
        stats.put("level", 1);
        stats.put("isResting", false);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of());

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent encounter = new ExplorationEvent();
        encounter.setEventKey("station_rat_encounter");
        encounter.setEventType("battle_placeholder");
        encounter.setName("变异站鼠");
        encounter.setWeight(1);
        encounter.setStaminaCost(5);
        encounter.setRepeatable(1);
        encounter.setEnabled(1);
        encounter.setRequiredConditionsJson("{}");
        encounter.setRisksJson("{\"monsterKey\":\"station_rat\"}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(encounter));

        assertThrows(BusinessException.class, () -> exploreService.startExplore(
                1L, "ruined_station", "battle-insufficient-stamina", 9L));

        assertEquals(3, stats.get("stamina"));
        verify(playerMapper, never()).updateById(player);
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void normalExplorationDoesNotReturnWhenRewardProgressWasNotSaved() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stamina", 20);
        stats.put("level", 1);
        stats.put("avatarRank", "F");
        stats.put("isResting", false);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of());
        when(playerMapper.updateById(player)).thenReturn(0);

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("resource_event");
        event.setEventType("resource");
        event.setName("车站补给");
        event.setWeight(1);
        event.setStaminaCost(5);
        event.setRepeatable(1);
        event.setEnabled(1);
        event.setRequiredConditionsJson("{}");
        event.setRewardsJson("{}");
        event.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));

        assertThrows(IllegalStateException.class, () -> exploreService.startExplore(
                1L, "ruined_station", "resource-save-failure", 9L));

        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void staminaCostReductionAccumulatesFractionalSavingsBeforeReducingOnePoint() {
        Map<String, Object> stageProgress = new LinkedHashMap<>();

        for (int attempt = 0; attempt < 6; attempt++) {
            assertEquals(5, ExploreServiceImpl.applySkillStaminaCostReduction(5, 0.03, stageProgress));
        }
        assertEquals(4, ExploreServiceImpl.applySkillStaminaCostReduction(5, 0.03, stageProgress));
        assertEquals(0.05, (double) stageProgress.get("skillStaminaSavingsRemainder"), 1.0e-8);
    }

    @Test
    void staminaCostReductionIsClampedAndNeverMakesAnActionFree() {
        Map<String, Object> stageProgress = new LinkedHashMap<>();

        assertEquals(1, ExploreServiceImpl.applySkillStaminaCostReduction(5, 1.0, stageProgress));
    }

    @Test
    void storyEventBonusPreservesFractionalWeight() {
        assertEquals(10.3, ExploreServiceImpl.applyStoryEventBonusToWeight(10.0, 0.03), 1.0e-9);
        assertEquals(10.0, ExploreServiceImpl.applyStoryEventBonusToWeight(10.0, 0.0), 1.0e-9);
    }

    @Test
    void equippedStoryEventBonusStacksWithSkillAndTitleBonuses() {
        double combinedBonus = ExploreServiceImpl.combineEffectBonus(
                Map.of("storyEventBonus", 0.03),
                Map.of("storyEventBonus", 0.02),
                Map.of("storyEventBonus", 0.05),
                "storyEventBonus");

        assertEquals(0.10, combinedBonus, 1.0e-9);
        assertEquals(11.0,
                ExploreServiceImpl.applyStoryEventBonusToWeight(10.0, combinedBonus), 1.0e-9);
    }

    @Test
    void storyPityIncreasesMinimumIntegerWeightAndClampsOverflow() {
        assertEquals(1, ExploreServiceImpl.applyStoryPityToWeight(1, 0));
        assertEquals(2, ExploreServiceImpl.applyStoryPityToWeight(1, 1));
        assertEquals(8, ExploreServiceImpl.applyStoryPityToWeight(1, 7));
        assertEquals(9, ExploreServiceImpl.applyStoryPityToWeight(5, 2));
        assertEquals(5, ExploreServiceImpl.applyStoryPityToWeight(5, -1));
        assertEquals(Integer.MAX_VALUE, ExploreServiceImpl.applyStoryPityToWeight(
                Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void hardStoryPitySelectsOnlyAmongAvailableStoriesWithoutDatabaseOrderBias() {
        ExplorationEvent firstStory = new ExplorationEvent();
        firstStory.setEventKey("story_first");
        firstStory.setEventType("story");
        firstStory.setWeight(10);
        ExplorationEvent secondStory = new ExplorationEvent();
        secondStory.setEventKey("story_second");
        secondStory.setEventType("story");
        secondStory.setWeight(10);
        ExplorationEvent resource = new ExplorationEvent();
        resource.setEventKey("resource_event");
        resource.setEventType("resource");
        resource.setWeight(10_000);

        Set<String> selectedStories = new HashSet<>();
        Random random = new Random(20260925L);
        for (int attempt = 0; attempt < 20; attempt++) {
            ExplorationEvent selected = ExploreServiceImpl.selectEvent(
                    List.of(firstStory, secondStory, resource), 8, Map.of(), 0.0, random);
            assertTrue(selected.getEventType().equals("story"));
            selectedStories.add(selected.getEventKey());
        }

        assertEquals(Set.of("story_first", "story_second"), selectedStories);
    }

    @Test
    void nonRepeatableHiddenExplorationEventIsNotAvailableAgainAfterItIsTriggered() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stamina", 20);
        stats.put("level", 1);
        stats.put("avatarRank", "F");
        stats.put("isResting", false);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());
        ExplorationEvent hiddenEvent = new ExplorationEvent();
        hiddenEvent.setEventKey("station_hidden_cache");
        hiddenEvent.setEventType("hidden");
        hiddenEvent.setName("墙后的补给箱");
        hiddenEvent.setWeight(1);
        hiddenEvent.setStaminaCost(1);
        hiddenEvent.setRepeatable(0);
        hiddenEvent.setEnabled(1);
        hiddenEvent.setRequiredConditionsJson("{}");
        hiddenEvent.setRewardsJson("{}");
        hiddenEvent.setProgressEffectsJson("{}");
        ExplorationEvent fallbackEvent = new ExplorationEvent();
        fallbackEvent.setEventKey("repeatable_fallback");
        fallbackEvent.setEventType("resource");
        fallbackEvent.setName("备用补给");
        fallbackEvent.setWeight(1);
        fallbackEvent.setStaminaCost(1);
        fallbackEvent.setRepeatable(1);
        fallbackEvent.setEnabled(1);
        fallbackEvent.setRequiredConditionsJson("{}");
        fallbackEvent.setRewardsJson("{}");
        fallbackEvent.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any()))
                .thenReturn(java.util.List.of(hiddenEvent), java.util.List.of(hiddenEvent, fallbackEvent));
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.List.of());

        Map<String, Object> first = exploreService.startExplore(1L, "ruined_station", "hidden-first", 9L);
        Map<String, Object> second = exploreService.startExplore(1L, "ruined_station", "hidden-repeat", 9L);
        JsonNode progress = new ObjectMapper().readTree(player.getStageProgressJson());

        assertEquals(true, ((Map<?, ?>) first.get("progress_effects")).get("hiddenEventsTriggered"));
        assertEquals(1, progress.path("hiddenEventsTriggered").size());
        assertEquals("station_hidden_cache", progress.path("hiddenEventsTriggered").get(0).asText());
        assertFalse(((Map<?, ?>) second.get("progress_effects")).containsKey("hiddenEventsTriggered"));
        assertEquals("repeatable_fallback", ((Map<?, ?>) second.get("result")).get("event_key"));
        assertEquals(2, progress.path("sideEventsTriggered").size());
        assertEquals("station_hidden_cache", progress.path("sideEventsTriggered").get(0).asText());
    }

    @Test
    void hiddenExplorationProgressIsIndependentForEachPlayersTimeline() throws Exception {
        Player firstPlayer = hiddenEventPlayer(31L, 41L);
        Player secondPlayer = hiddenEventPlayer(32L, 42L);
        when(playerMapper.selectByIdForUpdate(31L)).thenReturn(firstPlayer);
        when(playerMapper.selectByIdForUpdate(32L)).thenReturn(secondPlayer);
        when(recoveryService.applyRecovery(firstPlayer)).thenReturn(explorationStats());
        when(recoveryService.applyRecovery(secondPlayer)).thenReturn(explorationStats());

        Location location = new Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        ExplorationEvent hiddenEvent = new ExplorationEvent();
        hiddenEvent.setEventKey("station_hidden_cache");
        hiddenEvent.setEventType("hidden");
        hiddenEvent.setName("墙后的补给箱");
        hiddenEvent.setWeight(1);
        hiddenEvent.setStaminaCost(1);
        hiddenEvent.setRepeatable(0);
        hiddenEvent.setEnabled(1);
        hiddenEvent.setRequiredConditionsJson("{}");
        hiddenEvent.setRewardsJson("{}");
        hiddenEvent.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(List.of(hiddenEvent));
        when(titleService.evaluateAndUnlockTitles(31L, null)).thenReturn(List.of());
        when(titleService.evaluateAndUnlockTitles(32L, null)).thenReturn(List.of());

        Map<String, Object> firstResult = exploreService.startExplore(
                31L, "ruined_station", "hidden-first-player", 41L);
        Map<String, Object> secondResult = exploreService.startExplore(
                32L, "ruined_station", "hidden-second-player", 42L);

        assertEquals("station_hidden_cache", ((Map<?, ?>) firstResult.get("result")).get("event_key"));
        assertEquals("station_hidden_cache", ((Map<?, ?>) secondResult.get("result")).get("event_key"));
        assertEquals("station_hidden_cache", new ObjectMapper().readTree(firstPlayer.getStageProgressJson())
                .path("hiddenEventsTriggered").get(0).asText());
        assertEquals("station_hidden_cache", new ObjectMapper().readTree(secondPlayer.getStageProgressJson())
                .path("hiddenEventsTriggered").get(0).asText());
    }

    @Test
    void boatmanClueIsRecordedInThePlayersTimelineAndExplainedInTheExploreLog() throws Exception {
        Player player = hiddenEventPlayer(51L, 61L);
        player.setCurrentLocation("gwangjin_bridge");
        when(playerMapper.selectByIdForUpdate(51L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(explorationStats());
        when(playerLogService.findPendingBattleEncounters(51L)).thenReturn(List.of());

        Location location = new Location();
        location.setLocationKey("gwangjin_bridge");
        location.setName("广津大桥");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        ExplorationEvent boatmanStory = new ExplorationEvent();
        boatmanStory.setEventKey("gwangjin_side_boat");
        boatmanStory.setEventType("side_story");
        boatmanStory.setName("桥下的渡船");
        boatmanStory.setWeight(1);
        boatmanStory.setStaminaCost(1);
        boatmanStory.setRepeatable(0);
        boatmanStory.setEnabled(1);
        boatmanStory.setRequiredConditionsJson("{}");
        boatmanStory.setRewardsJson("{}");
        boatmanStory.setProgressEffectsJson("{}");
        boatmanStory.setLogTemplate("你听完了渡船人的话。");
        when(eventMapper.selectList(any())).thenReturn(List.of(boatmanStory));
        when(titleService.evaluateAndUnlockTitles(51L, null)).thenReturn(List.of());

        Map<String, Object> result = exploreService.startExplore(
                51L, "gwangjin_bridge", "boatman-clue-test", 61L);

        JsonNode progress = new ObjectMapper().readTree(player.getStageProgressJson());
        assertEquals("gwangjin_side_boat", progress.path("sideEventsTriggered").get(0).asText());
        @SuppressWarnings("unchecked")
        Map<String, Object> appliedProgress = (Map<String, Object>) result.get("progress_effects");
        assertTrue(((String) appliedProgress.get("storyRelationHint")).contains("25%"));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstLog = (Map<String, Object>) ((List<?>) result.get("new_logs")).get(0);
        assertTrue(((String) firstLog.get("message")).contains("最多 5 点"));
        assertTrue(((String) firstLog.get("message")).contains("撤退"));
    }

    private static Player hiddenEventPlayer(Long playerId, Long userId) {
        Player player = new Player();
        player.setId(playerId);
        player.setUserId(userId);
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        return player;
    }

    private static Map<String, Object> explorationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stamina", 20);
        stats.put("level", 1);
        stats.put("avatarRank", "F");
        stats.put("isResting", false);
        return stats;
    }

    @Test
    void unresolvedRepeatableStoryIsNotTriggeredAgainOrAllowedToOverwriteItsSnapshot() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"repeatable_story\"],"
                + "\"pendingStorySnapshots\":{\"repeatable_story\":{"
                + "\"instanceKey\":\"pending-instance\",\"rewards\":{\"coins\":12}}}}");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stamina", 20);
        stats.put("level", 1);
        stats.put("avatarRank", "D");
        stats.put("isResting", false);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent pendingStory = new ExplorationEvent();
        pendingStory.setEventKey("repeatable_story");
        pendingStory.setEventType("story");
        pendingStory.setName("重复触发的剧情");
        pendingStory.setWeight(100);
        pendingStory.setStaminaCost(5);
        pendingStory.setRepeatable(1);
        pendingStory.setEnabled(1);
        pendingStory.setRequiredConditionsJson("{}");
        pendingStory.setChoicesJson("[{\"label\":\"继续\"}]");

        ExplorationEvent resourceEvent = new ExplorationEvent();
        resourceEvent.setEventKey("resource_event");
        resourceEvent.setEventType("resource");
        resourceEvent.setName("车站补给");
        resourceEvent.setWeight(1);
        resourceEvent.setStaminaCost(1);
        resourceEvent.setRepeatable(1);
        resourceEvent.setEnabled(1);
        resourceEvent.setRequiredConditionsJson("{}");
        resourceEvent.setRewardsJson("{}");
        resourceEvent.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(pendingStory, resourceEvent));
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.Collections.emptyList());

        Map<String, Object> result = exploreService.startExplore(
                1L, "ruined_station", "avoid-pending-story-overwrite", 9L);

        assertEquals("resource_event", ((Map<?, ?>) result.get("result")).get("event_key"));
        assertEquals("pending-instance", new ObjectMapper().readTree(player.getStageProgressJson())
                .path("pendingStorySnapshots").path("repeatable_story").path("instanceKey").asText());
    }

    @Test
    void equippedSkillReducesActualExplorationCostAndLogsWhatWasPaid() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stamina", 20);
        stats.put("level", 1);
        stats.put("avatarRank", "D");
        stats.put("isResting", false);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);

        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(java.util.List.of());

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("stamina_test_event");
        event.setEventType("resource");
        event.setName("体力测试事件");
        event.setDescription("验证技能减免后的实际扣除");
        event.setWeight(1);
        event.setStaminaCost(5);
        event.setRepeatable(1);
        event.setEnabled(1);
        event.setRequiredConditionsJson("{}");
        event.setRewardsJson("{\"channelHeat\":20}");
        event.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of(
                "staminaCostReduce", 0.08,
                "storyEventBonus", 0.03,
                "channelHeatGainRate", 0.08));
        when(equipmentService.calculateEquipmentSpecialEffectBonuses(1L)).thenReturn(Map.of(
                "storyEventBonus", 0.05));
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of("channelHeatGainRate", 0.05));
        when(playerEffectService.applyChannelHeatGainRate(1L, 20)).thenReturn(23);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.List.of());

        Map<String, Object> first = exploreService.startExplore(1L, "ruined_station", "skill-cost-1", 9L);
        Map<String, Object> second = exploreService.startExplore(1L, "ruined_station", "skill-cost-2", 9L);
        Map<String, Object> third = exploreService.startExplore(1L, "ruined_station", "skill-cost-3", 9L);

        assertEquals("skill-cost-1", first.get("request_key"));
        assertFalse((Boolean) first.get("request_replayed"));
        assertEquals(5, first.get("stamina_cost_paid"));
        assertEquals(5, second.get("stamina_cost_paid"));
        assertEquals(4, third.get("stamina_cost_paid"));
        assertEquals(6, stats.get("stamina"));
        assertEquals(69, stats.get("channelHeat"));
        assertEquals("体力测试事件（消耗体力4）",
                ((Map<?, ?>) ((java.util.List<?>) third.get("new_logs")).getFirst()).get("message"));
        verify(playerMapper, times(3)).selectByIdForUpdate(1L);
        verify(equipmentService, times(3)).calculateEquipmentSpecialEffectBonuses(1L);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> logMetadata =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService, times(3)).addLog(eq(1L), anyString(), anyString(), logMetadata.capture());
        Map<String, Object> lastMetadata = logMetadata.getAllValues().getLast();
        Map<String, Object> firstMetadata = logMetadata.getAllValues().getFirst();
        assertEquals("skill-cost-1", firstMetadata.get("requestKey"));
        assertEquals("ruined_station", firstMetadata.get("requestLocationKey"));
        assertTrue(firstMetadata.containsKey("requestResponse"));
        assertEquals("exploration_result", lastMetadata.get("kind"));
        assertEquals("stamina_test_event", lastMetadata.get("eventKey"));
        assertEquals(4, lastMetadata.get("staminaCost"));
        assertEquals(23, ((Map<?, ?>) lastMetadata.get("rewards")).get("channelHeat"));
    }

    @Test
    void explorationRewardRejectsChannelHeatOverflowBeforeSavingAnyState() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setCurrentLocation("ruined_station");
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("stamina", 20);
        stats.put("level", 1);
        stats.put("avatarRank", "F");
        stats.put("isResting", false);
        stats.put("channelHeat", Integer.MAX_VALUE);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);

        Location location = new Location();
        location.setLocationKey("ruined_station");
        location.setName("废弃车站");
        location.setMinLevel(1);
        location.setUnlockConditionsJson("{}");
        location.setEventRatesJson("{}");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("heat_boundary_event");
        event.setEventType("resource");
        event.setName("热度边界事件");
        event.setWeight(1);
        event.setStaminaCost(1);
        event.setRepeatable(1);
        event.setEnabled(1);
        event.setRequiredConditionsJson("{}");
        event.setRewardsJson("{\"channelHeat\":1}");
        event.setProgressEffectsJson("{}");
        when(eventMapper.selectList(any())).thenReturn(List.of(event));
        when(playerEffectService.applyChannelHeatGainRate(1L, 1)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.startExplore(1L, "ruined_station", "heat-boundary", 9L));

        assertEquals("星流热度超出可保存范围，操作无法完成", error.getMessage());
        assertEquals("{}", player.getStatsJson());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any());
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void completedOneTimeStoryIsNotRestoredEvenWhenSnapshotIsStale() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"already_chosen\"],"
                + "\"pendingStorySnapshots\":{\"already_chosen\":{\"rewards\":{\"coins\":99}}}}");
        when(playerMapper.selectById(1L)).thenReturn(player);

        PlayerStoryLog completedLog = new PlayerStoryLog();
        completedLog.setEventKey("already_chosen");
        completedLog.setChoiceIndex(0);
        when(storyLogMapper.selectList(any())).thenReturn(java.util.List.of(completedLog));

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("already_chosen");
        event.setEventType("story");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"已选过\"}]");
        when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));

        var pending = exploreService.getPendingStories(1L, 9L);

        assertEquals(java.util.Collections.emptyList(), pending);
    }

    @Test
    void choosingPendingStoryRemovesSnapshotAndAppliesOnlyChoiceRewards() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(20);
        player.setStatsJson("{\"exp\":0,\"level\":1,\"freePoints\":0,\"maxHp\":100,\"channelHeat\":0,\"worldLineShift\":4}");
        player.setStoryFlagsJson("{}");
        player.setDecisionHistoryJson("[]");
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"choice_reward\"],"
                + "\"pendingStorySnapshots\":{\"choice_reward\":{\"rewards\":{\"coins\":12}}}}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("choice_reward");
        event.setEventType("story");
        event.setName("待选剧情");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"留下记录\",\"rewards_override\":{\"coins\":5,\"exp\":0,\"channelHeat\":10,\"worldLineShift\":-2,\"items\":[{\"itemKey\":\"constellation_chart\",\"quantity\":1,\"dropRate\":1.0}],\"equipment\":[{\"equipmentKey\":\"starfall_blade\",\"dropRate\":1.0}]}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(playerEffectService.applyChannelHeatGainRate(1L, 10)).thenReturn(11);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.Collections.emptyList());
        when(equipmentService.addEquipment(1L, "starfall_blade", "story_choice")).thenReturn(1);
        when(inventoryService.addItemWithResult(1L, "constellation_chart", 1))
                .thenReturn(new ItemGrantResult(1, 1, "星座图表"));
        EquipmentVO starfallBlade = new EquipmentVO();
        starfallBlade.setEquipmentKey("starfall_blade");
        starfallBlade.setName("星陨之刃");
        when(equipmentService.getAllEquipment()).thenReturn(java.util.List.of(starfallBlade));

        var result = exploreService.makeChoice(1L, "choice_reward", 0, "choice-reward-once", 9L);

        assertEquals(25, player.getCoins());
        assertEquals(5, ((java.util.Map<?, ?>) result.get("rewards")).get("coins"));
        assertEquals(11, ((java.util.Map<?, ?>) result.get("rewards")).get("channelHeat"));
        assertEquals(-2, ((java.util.Map<?, ?>) result.get("rewards")).get("worldLineShift"));
        java.util.Map<?, ?> acquiredItem = (java.util.Map<?, ?>)
                ((java.util.List<?>) ((java.util.Map<?, ?>) result.get("rewards")).get("items")).get(0);
        assertEquals("星座图表", acquiredItem.get("itemName"));
        java.util.Map<?, ?> acquiredEquipment = (java.util.Map<?, ?>)
                ((java.util.List<?>) ((java.util.Map<?, ?>) result.get("rewards")).get("equipment")).get(0);
        assertEquals("starfall_blade", acquiredEquipment.get("equipmentKey"));
        assertEquals("星陨之刃", acquiredEquipment.get("equipmentName"));
        assertEquals(false, ((java.util.Map<?, ?>) result.get("rewards")).containsKey("exp"));
        var stats = new ObjectMapper().readTree(player.getStatsJson());
        assertEquals(0, stats.path("exp").asInt());
        assertEquals(11, stats.path("channelHeat").asInt());
        assertEquals(2, stats.path("worldLineShift").asInt());
        assertEquals(100, stats.path("maxExp").asInt());
        verify(playerLogService, never()).addLog(eq(1L), eq("milestone"), any());
        assertEquals(false, new ObjectMapper().readTree(player.getStageProgressJson())
                .path("pendingStorySnapshots").has("choice_reward"));
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> choiceMetadata =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("story"), eq("你选择了: 留下记录"), choiceMetadata.capture());
        assertEquals("story_choice", choiceMetadata.getValue().get("kind"));
        assertEquals("choice_reward", choiceMetadata.getValue().get("eventKey"));
        assertEquals(11, ((Map<?, ?>) choiceMetadata.getValue().get("rewards")).get("channelHeat"));
        assertEquals(-2, ((Map<?, ?>) choiceMetadata.getValue().get("rewards")).get("worldLineShift"));
        java.util.Map<?, ?> loggedItem = (java.util.Map<?, ?>)
                ((java.util.List<?>) ((Map<?, ?>) choiceMetadata.getValue().get("rewards")).get("items")).get(0);
        assertEquals("星座图表", loggedItem.get("itemName"));
        java.util.Map<?, ?> loggedEquipment = (java.util.Map<?, ?>)
                ((java.util.List<?>) ((Map<?, ?>) choiceMetadata.getValue().get("rewards")).get("equipment")).get(0);
        assertEquals("星陨之刃", loggedEquipment.get("equipmentName"));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(1L, "choice_reward", 0, "choice-reward-again", 9L));

        assertEquals("该剧情已经完成，不能重复选择", duplicate.getMessage());
        assertEquals(25, player.getCoins());
        verify(playerMapper, times(2)).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        verify(playerLogService, times(1)).addLog(eq(1L), eq("story"), eq("你选择了: 留下记录"), anyMap());
        verify(equipmentService, org.mockito.Mockito.times(1))
                .addEquipment(1L, "starfall_blade", "story_choice");
        org.mockito.Mockito.verifyNoInteractions(broadcastService, factionService, questService);
    }

    @Test
    void storyChoiceDoesNotReturnWhenPlayerProgressWasNotSaved() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(20);
        player.setStatsJson("{\"exp\":0,\"level\":1,\"freePoints\":0,\"maxHp\":100}");
        player.setStoryFlagsJson("{}");
        player.setDecisionHistoryJson("[]");
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"choice_reward\"],"
                + "\"pendingStorySnapshots\":{\"choice_reward\":{\"instanceKey\":\"story-instance-1\",\"rewards\":{}}}}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(0);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("choice_reward");
        event.setEventType("story");
        event.setName("待选剧情");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"留下记录\",\"rewards_override\":{\"coins\":5}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);

        assertThrows(IllegalStateException.class, () -> exploreService.makeChoice(
                1L, "choice_reward", 0, "choice-save-failure", "story-instance-1", 9L));

        verify(playerMapper).updateById(player);
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void staleStoryInstanceCannotApplyChoiceRewardsToTheCurrentPendingStory() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(20);
        player.setStatsJson("{}");
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"repeatable_story\"],"
                + "\"pendingStorySnapshots\":{\"repeatable_story\":{"
                + "\"instanceKey\":\"current-instance\",\"rewards\":{}}}}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("repeatable_story");
        event.setEventType("story");
        event.setName("重复触发的剧情");
        event.setRepeatable(1);
        event.setChoicesJson("[{\"label\":\"继续\",\"rewards_override\":{\"coins\":5}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);

        BusinessException error = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "repeatable_story", 0, "old-choice-request", "old-instance", 9L));

        assertEquals("该剧情已结束或不再是当前待选剧情", error.getMessage());
        assertEquals(20, player.getCoins());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(storyLogMapper, never()).insert(any(PlayerStoryLog.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void storyChoiceRetryReplaysTheSameInstanceWithoutGrantingRewardsAgain() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(20);
        player.setStatsJson("{\"exp\":0,\"level\":1,\"freePoints\":0,\"maxHp\":100}");
        player.setStoryFlagsJson("{}");
        player.setDecisionHistoryJson("[]");
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"choice_reward\"],"
                + "\"pendingStorySnapshots\":{\"choice_reward\":{"
                + "\"instanceKey\":\"story-instance-1\",\"rewards\":{}}}}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("choice_reward");
        event.setEventType("story");
        event.setName("待选剧情");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"留下记录\",\"rewards_override\":{\"coins\":5}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(java.util.Collections.emptyList());

        var first = exploreService.makeChoice(
                1L, "choice_reward", 0, "story-choice-retry-1", "story-instance-1", 9L);
        org.mockito.ArgumentCaptor<Map<String, Object>> receiptCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("story"), eq("你选择了: 留下记录"), receiptCaptor.capture());
        Map<String, Object> receipt = receiptCaptor.getValue();
        when(playerLogService.findStoryChoiceReceipt(1L, "story-choice-retry-1")).thenReturn(receipt);

        var replay = exploreService.makeChoice(
                1L, "choice_reward", 0, "story-choice-retry-1", "story-instance-1", 9L);

        assertEquals(25, player.getCoins());
        assertEquals("story-instance-1", first.get("story_instance_key"));
        assertEquals(false, first.get("request_replayed"));
        assertEquals(true, replay.get("request_replayed"));
        assertEquals("留下记录", replay.get("choice_label"));
        assertEquals(5, ((Map<?, ?>) replay.get("rewards")).get("coins"));
        assertEquals("story-instance-1", receipt.get("storyInstanceKey"));
        verify(playerMapper, times(2)).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        verify(playerLogService, times(1)).addLog(eq(1L), eq("story"), eq("你选择了: 留下记录"), anyMap());

        BusinessException mismatchedRetry = assertThrows(BusinessException.class,
                () -> exploreService.makeChoice(
                        1L, "choice_reward", 0, "story-choice-retry-1", "story-instance-2", 9L));
        assertEquals("该请求键已用于其他剧情实例或选项，请重新进入当前剧情后再试", mismatchedRetry.getMessage());
        assertEquals(25, player.getCoins());
        verify(playerMapper, times(3)).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        verify(playerLogService, times(1)).addLog(eq(1L), eq("story"), eq("你选择了: 留下记录"), anyMap());
    }

    @Test
    void personalStoryChoicesWriteOnlyTheirOwnFlagsAndLaterEchoesStayOnEachTimeline() throws Exception {
        Player interwoven = pendingPaidStoryPlayer(0, 0);
        interwoven.setId(31L);
        interwoven.setStatsJson("{\"level\":20,\"stamina\":50,\"exp\":0,\"freePoints\":0,\"maxHp\":100}");
        interwoven.setDecisionHistoryJson("[{\"event_key\":\"darkest_spring_forest_vision\",\"choice_index\":0}]");
        interwoven.setStageProgressJson("{\"storyEventsTriggered\":[\"darkest_spring_first_omen\","
                + "\"darkest_spring_forest_vision\",\"darkest_spring_story_stitch\"],"
                + "\"sideEventsTriggered\":[\"geumho_echo_whisper\"],"
                + "\"pendingStorySnapshots\":{\"darkest_spring_story_stitch\":{"
                + "\"instanceKey\":\"stitch-31\",\"rewards\":{}}}}");
        Player separated = pendingPaidStoryPlayer(0, 0);
        separated.setId(32L);
        separated.setStatsJson("{\"level\":20,\"stamina\":50,\"exp\":0,\"freePoints\":0,\"maxHp\":100}");
        separated.setDecisionHistoryJson("[{\"event_key\":\"darkest_spring_forest_vision\",\"choice_index\":1}]");
        separated.setStageProgressJson("{\"storyEventsTriggered\":[\"darkest_spring_first_omen\","
                + "\"darkest_spring_forest_vision\",\"darkest_spring_story_stitch\"],"
                + "\"sideEventsTriggered\":[\"geumho_echo_whisper\"],"
                + "\"pendingStorySnapshots\":{\"darkest_spring_story_stitch\":{"
                + "\"instanceKey\":\"stitch-32\",\"rewards\":{}}}}");
        when(playerMapper.selectByIdForUpdate(31L)).thenReturn(interwoven);
        when(playerMapper.selectByIdForUpdate(32L)).thenReturn(separated);

        ExplorationEvent stitch = new ExplorationEvent();
        stitch.setEventKey("darkest_spring_story_stitch");
        stitch.setEventType("story");
        stitch.setName("交错的两段记录");
        stitch.setRepeatable(0);
        stitch.setChoicesJson("[{\"label\":\"并置\",\"set_story_flags\":{"
                + "\"story_dark_spring_interwoven\":true}},{\"label\":\"分别保存\","
                + "\"set_story_flags\":{\"story_dark_spring_separated\":true}}]");
        when(eventMapper.selectOne(any())).thenReturn(stitch);
        when(titleService.evaluateAndUnlockTitles(31L, null)).thenReturn(List.of());
        when(titleService.evaluateAndUnlockTitles(32L, null)).thenReturn(List.of());

        exploreService.makeChoice(31L, stitch.getEventKey(), 0,
                "stitch-choice-31", "stitch-31", 9L);
        exploreService.makeChoice(32L, stitch.getEventKey(), 1,
                "stitch-choice-32", "stitch-32", 9L);

        // A private narrative choice only changes that player's timeline; it is not collective activity.
        org.mockito.Mockito.verifyNoInteractions(broadcastService, factionService, questService);

        JsonNode interwovenFlags = new ObjectMapper().readTree(interwoven.getStoryFlagsJson());
        JsonNode separatedFlags = new ObjectMapper().readTree(separated.getStoryFlagsJson());
        assertTrue(interwovenFlags.path("story_dark_spring_interwoven").asBoolean());
        assertFalse(interwovenFlags.path("story_dark_spring_separated").asBoolean());
        assertTrue(separatedFlags.path("story_dark_spring_separated").asBoolean());
        assertFalse(separatedFlags.path("story_dark_spring_interwoven").asBoolean());

        ExplorationEvent laterStory = new ExplorationEvent();
        laterStory.setEventKey("darkest_spring_abyss_gate");
        laterStory.setEventType("story");
        laterStory.setName("恶魔之门的回响");
        laterStory.setRepeatable(0);
        laterStory.setWeight(1);
        laterStory.setStaminaCost(10);
        laterStory.setEnabled(1);
        laterStory.setRewardsJson("{}");
        laterStory.setProgressEffectsJson("{}");
        laterStory.setChoicesJson("[{\"label\":\"继续\"}]");
        laterStory.setRequiredConditionsJson("{\"choice_unlockers\":["
                + "{\"event_key\":\"darkest_spring_forest_vision\",\"choice_indices\":[0,1]}],"
                + "\"personal_contexts\":["
                + "{\"story_flag\":\"story_dark_spring_interwoven\",\"text\":\"两段记录在你的时间线里接续\"},"
                + "{\"story_flag\":\"story_dark_spring_separated\",\"text\":\"两段记录仍分别保存\"}]}");
        Location demonGate = new Location();
        demonGate.setLocationKey("demon_gate");
        demonGate.setName("恶魔之门");
        demonGate.setMinLevel(1);
        demonGate.setUnlockConditionsJson("{}");
        demonGate.setEventRatesJson("{\"story\":100}");
        when(locationMapper.selectOne(any())).thenReturn(demonGate);
        when(locationMapper.selectList(any())).thenReturn(List.of());
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                new LinkedHashMap<>(Map.of("level", 20, "stamina", 50,
                        "avatarRank", "F", "isResting", false)));
        when(skillService.calculateSkillBonus(any())).thenReturn(Map.of());
        when(titleService.calculateTitleEffects(any())).thenReturn(Map.of());
        when(titleService.evaluateAndUnlockTitles(any(), eq(null))).thenReturn(List.of());
        when(storyLogMapper.selectList(any())).thenReturn(List.of());
        when(eventMapper.selectList(any())).thenReturn(List.of(laterStory));

        Map<String, Object> interwovenEncounter = exploreService.startExplore(
                31L, "demon_gate", "demon-gate-route-31", 9L);
        Map<String, Object> separatedEncounter = exploreService.startExplore(
                32L, "demon_gate", "demon-gate-route-32", 9L);
        assertEquals("darkest_spring_abyss_gate",
                ((Map<?, ?>) interwovenEncounter.get("result")).get("event_key"));
        assertEquals("darkest_spring_abyss_gate",
                ((Map<?, ?>) separatedEncounter.get("result")).get("event_key"));
        assertEquals(List.of("两段记录在你的时间线里接续"),
                ((Map<?, ?>) interwovenEncounter.get("result")).get("personal_contexts"));
        assertEquals(List.of("两段记录仍分别保存"),
                ((Map<?, ?>) separatedEncounter.get("result")).get("personal_contexts"));

        when(playerMapper.selectById(31L)).thenReturn(interwoven);
        when(playerMapper.selectById(32L)).thenReturn(separated);
        List<Map<String, Object>> interwovenPending = exploreService.getPendingStories(31L, 9L);
        List<Map<String, Object>> separatedPending = exploreService.getPendingStories(32L, 9L);

        assertEquals(List.of("两段记录在你的时间线里接续"),
                ((Map<?, ?>) interwovenPending.get(0).get("result")).get("personal_contexts"));
        assertEquals(List.of("两段记录仍分别保存"),
                ((Map<?, ?>) separatedPending.get(0).get("result")).get("personal_contexts"));
    }

    @Test
    void storyChoicePersistsEndingFlagsOnOnlyTheChoosingPlayersTimelineAndLog() throws Exception {
        Player player = pendingPaidStoryPlayer(0, 0);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("paid_story");
        event.setEventType("story");
        event.setName("履行终局约定");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"履行约定\",\"consequence_text\":\"旧梦结束。\","
                + "\"set_story_flags\":{\"ending_old_dream_ended\":true}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(List.of());

        Map<String, Object> result = exploreService.makeChoice(
                1L, "paid_story", 0, "ending-flag-write", "paid-story-instance", 9L);

        JsonNode storyFlags = new ObjectMapper().readTree(player.getStoryFlagsJson());
        assertTrue(storyFlags.path("ending_old_dream_ended").asBoolean());
        assertEquals(Map.of("ending_old_dream_ended", true), result.get("story_flags_updated"));
        verify(playerMapper).updateById(player);

        org.mockito.ArgumentCaptor<Map<String, Object>> logCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("story"), eq("你选择了: 履行约定"), logCaptor.capture());
        assertEquals(Map.of("ending_old_dream_ended", true), logCaptor.getValue().get("storyFlagsUpdated"));

        when(playerLogService.findStoryChoiceReceipt(1L, "ending-flag-write"))
                .thenReturn(logCaptor.getValue());
        Map<String, Object> replay = exploreService.makeChoice(
                1L, "paid_story", 0, "ending-flag-write", "paid-story-instance", 9L);

        assertEquals(true, replay.get("request_replayed"));
        assertEquals(Map.of("ending_old_dream_ended", true), replay.get("story_flags_updated"));
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        verify(playerLogService, org.mockito.Mockito.times(1))
                .addLog(eq(1L), eq("story"), eq("你选择了: 履行约定"), anyMap());
    }

    @Test
    void storyRiskDamageAndPendingBattleAreCommittedOnceAndRecoveredFromTheSameReceipt() throws Exception {
        Player player = pendingPaidStoryPlayer(0, 0);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"iron_king_story\"],"
                + "\"pendingStorySnapshots\":{\"iron_king_story\":{"
                + "\"instanceKey\":\"iron-king-instance\",\"rewards\":{}}}}");
        player.setStatsJson("{\"exp\":0,\"level\":5,\"maxHp\":100,\"hp\":80}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("iron_king_story");
        event.setEventType("story");
        event.setLocationKey("h2_test_zone");
        event.setName("忠武路的铁王");
        event.setRepeatable(0);
        event.setChoicesJson("[{\"label\":\"拒绝下跪\",\"consequence_text\":\"铁王的投影挡在前方。\","
                + "\"risks\":{\"damagePercent\":15,\"monsterKey\":\"iron_king_trial\","
                + "\"battleName\":\"铁王的钢铁化试炼\","
                + "\"battleDescription\":\"一道钢铁投影拦住了你。\"}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);
        var location = new com.huazhenghai.readergame.entity.Location();
        location.setLocationKey("h2_test_zone");
        location.setName("隔离测试区");
        when(locationMapper.selectOne(any())).thenReturn(location);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(List.of());

        Map<String, Object> first = exploreService.makeChoice(
                1L, "iron_king_story", 0, "iron-king-choice-once", "iron-king-instance", 9L);
        var statsAfterFirst = new ObjectMapper().readTree(player.getStatsJson());
        assertEquals(65, statsAfterFirst.path("hp").asInt());
        assertEquals(15, ((Map<?, ?>) first.get("applied_risks")).get("damage"));
        assertEquals(80, ((Map<?, ?>) first.get("applied_risks")).get("hpBefore"));
        assertEquals(65, ((Map<?, ?>) first.get("applied_risks")).get("hpAfter"));
        Map<?, ?> firstBattle = (Map<?, ?>) first.get("pending_battle");
        assertEquals("battle", firstBattle.get("result_type"));
        assertEquals("iron_king_trial", firstBattle.get("monster_key"));
        assertEquals("h2_test_zone", firstBattle.get("location_key"));
        assertEquals("隔离测试区", firstBattle.get("location_name"));
        assertTrue(firstBattle.get("request_key") instanceof String);

        org.mockito.ArgumentCaptor<Map<String, Object>> receiptCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("story"), eq("你选择了: 拒绝下跪"), receiptCaptor.capture());
        Map<String, Object> receipt = receiptCaptor.getValue();
        when(playerLogService.findStoryChoiceReceipt(1L, "iron-king-choice-once")).thenReturn(receipt);

        Map<String, Object> replay = exploreService.makeChoice(
                1L, "iron_king_story", 0, "iron-king-choice-once", "iron-king-instance", 9L);

        assertEquals(true, replay.get("request_replayed"));
        assertEquals(first.get("pending_battle"), replay.get("pending_battle"));
        assertEquals(65, new ObjectMapper().readTree(player.getStatsJson()).path("hp").asInt());
        verify(playerMapper, times(2)).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(storyLogMapper).insert(any(PlayerStoryLog.class));
        verify(playerLogService, times(1))
                .addLog(eq(1L), eq("story"), eq("你选择了: 拒绝下跪"), anyMap());
    }

    @Test
    void storyRiskDamageCannotReducePlayerBelowOneHp() throws Exception {
        Player player = pendingPaidStoryPlayer(0, 0);
        player.setStageProgressJson("{\"storyEventsTriggered\":[\"iron_king_story\"]}");
        player.setStatsJson("{\"level\":5,\"maxHp\":100,\"hp\":10}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        ExplorationEvent event = new ExplorationEvent();
        event.setEventKey("iron_king_story");
        event.setEventType("story");
        event.setName("忠武路的铁王");
        event.setRepeatable(1);
        event.setChoicesJson("[{\"label\":\"拒绝下跪\",\"risks\":{\"damagePercent\":15}}]");
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(titleService.evaluateAndUnlockTitles(1L, null)).thenReturn(List.of());

        Map<String, Object> result = exploreService.makeChoice(
                1L, "iron_king_story", 0, "iron-king-nonlethal", 9L);

        assertEquals(1, new ObjectMapper().readTree(player.getStatsJson()).path("hp").asInt());
        assertEquals(9, ((Map<?, ?>) result.get("applied_risks")).get("damage"));
        assertEquals(true, ((Map<?, ?>) result.get("applied_risks")).get("nonLethal"));
    }
}
