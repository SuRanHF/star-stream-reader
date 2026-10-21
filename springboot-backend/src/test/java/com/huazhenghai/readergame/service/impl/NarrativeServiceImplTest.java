package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.NpcGhost;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerNpcEncounter;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.ItemMemoryMapper;
import com.huazhenghai.readergame.mapper.LocationEchoMapper;
import com.huazhenghai.readergame.mapper.NpcGhostMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerNpcEncounterMapper;
import com.huazhenghai.readergame.mapper.PlayerSkillMapper;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NarrativeServiceImplTest {

    @Mock private ItemMemoryMapper itemMemoryMapper;
    @Mock private LocationEchoMapper locationEchoMapper;
    @Mock private NpcGhostMapper npcGhostMapper;
    @Mock private PlayerNpcEncounterMapper encounterMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerSkillMapper skillMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private InventoryService inventoryService;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerActionReceiptService actionReceiptService;

    private NarrativeServiceImpl narrativeService;

    @BeforeEach
    void setUp() {
        narrativeService = new NarrativeServiceImpl(itemMemoryMapper, locationEchoMapper, npcGhostMapper,
                encounterMapper, playerMapper, skillMapper, itemMapper, new ObjectMapper(), inventoryService,
                playerLogService, actionReceiptService);
    }

    @Test
    void startupSeedAddsMissingNpcGhostsWithoutReplacingExistingConfiguration() {
        NpcGhost existing = new NpcGhost();
        existing.setGhostKey("hk_reader");
        existing.setName("本地自定义名称");
        AtomicInteger lookupCount = new AtomicInteger();
        when(npcGhostMapper.selectCount(null)).thenReturn(1L);
        when(npcGhostMapper.selectOne(any())).thenAnswer(invocation ->
                lookupCount.getAndIncrement() == 0 ? existing : null);

        narrativeService.seedNpcGhostsIfMissing();

        ArgumentCaptor<NpcGhost> inserted = ArgumentCaptor.forClass(NpcGhost.class);
        verify(npcGhostMapper, atLeastOnce()).insert(inserted.capture());
        assertTrue(inserted.getAllValues().stream().noneMatch(ghost -> "hk_reader".equals(ghost.getGhostKey())));
        verify(npcGhostMapper, atLeastOnce()).selectOne(any());
    }

    @Test
    void dialogueChoiceGrantsOnlyThroughInventoryServiceAndSavesNextBranch() {
        NpcGhost ghost = ghost(false);
        Item item = new Item();
        item.setItemKey("dream_shard");
        item.setName("梦境碎片");
        Player player = player();
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(0L);
        when(encounterMapper.selectOne(any())).thenReturn(null);
        when(encounterMapper.insert(any(PlayerNpcEncounter.class))).thenReturn(1);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryService.addItemWithResult(1L, "dream_shard", 1)).thenReturn(new ItemGrantResult(1, 1));

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0,
                "npc-choice-retry-1");

        assertEquals(1, result.get("nextNodeIndex"));
        var effectsApplied = (java.util.Map<?, ?>) result.get("effectsApplied");
        var applied = (java.util.List<?>) effectsApplied.get("applied");
        var grant = (java.util.Map<?, ?>) applied.getFirst();
        assertEquals("梦境碎片", grant.get("itemName"));
        assertEquals(1, grant.get("quantity"));
        org.mockito.ArgumentCaptor<String> logMessage = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(playerLogService).addLog(eq(1L), eq("story"), logMessage.capture(), anyMap());
        assertTrue(logMessage.getValue().contains("获得物品：梦境碎片"));

        ArgumentCaptor<PlayerNpcEncounter> saved = ArgumentCaptor.forClass(PlayerNpcEncounter.class);
        verify(encounterMapper).insert(saved.capture());
        assertTrue(saved.getValue().getChoiceMade().startsWith("[NARRATIVE node=0 choice=0 next=1]"));
        verify(inventoryService).addItemWithResult(1L, "dream_shard", 1);
        verify(actionReceiptService).record(eq(1L), eq("narrative.ghost.choice"), eq("npc-choice-retry-1"),
                any(), eq(result));
    }

    @Test
    void repeatedStableRequestReplaysOriginalResponseWithoutReapplyingEffects() {
        var replay = java.util.Map.<String, Object>of(
                "ghostKey", "reader_ghost", "nodeIndex", 0, "choiceIndex", 0,
                "playerChoice", "拿走碎片", "nextNodeIndex", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player());
        doReturn(replay).when(actionReceiptService).findReplay(eq(1L), eq("narrative.ghost.choice"),
                eq("npc-choice-retry-1"), any(), eq(java.util.Map.class));

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0,
                "npc-choice-retry-1");

        assertEquals(replay, result);
        verify(npcGhostMapper, never()).selectOne(any());
        verify(encounterMapper, never()).insert(any(PlayerNpcEncounter.class));
        verify(inventoryService, never()).addItemWithResult(1L, "dream_shard", 1);
        verify(playerLogService, never()).addLog(eq(1L), eq("story"), any(), anyMap());
    }

    @Test
    void npcStoryFlagIsNotReportedAsAppliedWhenPlayerUpdateFails() {
        NpcGhost ghost = ghost(false);
        ghost.setDialogueTreeJson("""
                [{"text":"第一段","choices":[{"text":"记住名字","response":"你记下了这个名字。",
                "effects":{"addStoryFlag":"ghost_name_known"},"leadToIndex":null}]}]
                """);
        Player player = player();
        player.setStoryFlagsJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(0);
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(0L);
        when(encounterMapper.selectOne(any())).thenReturn(null);
        when(encounterMapper.insert(any(PlayerNpcEncounter.class))).thenReturn(1);

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0);

        var effectsApplied = (java.util.Map<?, ?>) result.get("effectsApplied");
        assertTrue(((java.util.List<?>) effectsApplied.get("applied")).isEmpty());
        assertEquals(1, ((java.util.List<?>) effectsApplied.get("errors")).size());
        verify(playerMapper).updateById(player);
    }

    @Test
    void replayingTheSameChoiceDoesNotGrantItsRewardTwice() {
        NpcGhost ghost = ghost(false);
        PlayerNpcEncounter previous = new PlayerNpcEncounter();
        previous.setPlayerId(1L);
        previous.setGhostKey("reader_ghost");
        previous.setChoiceMade("[NARRATIVE node=0 choice=0 next=1] 拿走碎片");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player());
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(1L);
        when(encounterMapper.selectOne(any())).thenReturn(previous);

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0);

        assertEquals("choice_already_processed", result.get("error"));
        verify(encounterMapper, never()).insert(any(PlayerNpcEncounter.class));
        verify(inventoryService, never()).addItemWithResult(1L, "dream_shard", 1);
    }

    @Test
    void failedInventoryGrantIsReportedInsteadOfClaimedAsAnAcquiredItem() {
        NpcGhost ghost = ghost(false);
        Player player = player();
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(0L);
        when(encounterMapper.selectOne(any())).thenReturn(null);
        when(encounterMapper.insert(any(PlayerNpcEncounter.class))).thenReturn(1);
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(inventoryService.addItemWithResult(1L, "dream_shard", 1)).thenReturn(new ItemGrantResult(0, 0));

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0);

        var effectsApplied = (java.util.Map<?, ?>) result.get("effectsApplied");
        assertTrue(((java.util.List<?>) effectsApplied.get("applied")).isEmpty());
        assertEquals(1, ((java.util.List<?>) effectsApplied.get("errors")).size());
        verify(inventoryService).addItemWithResult(1L, "dream_shard", 1);
    }

    @Test
    void npcStatOverflowDoesNotPersistOrReportEarlierPendingChanges() throws Exception {
        NpcGhost ghost = ghost(false);
        ghost.setDialogueTreeJson("""
                [{"text":"第一段","choices":[{"text":"继续","response":"测试响应。","effects":{
                  "statChange":{"defense":1,"heat":1}
                }}]}]
                """);
        Player player = player();
        String originalStats = "{\"defense\":5,\"channelHeat\":2147483647}";
        player.setStatsJson(originalStats);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(0L);
        when(encounterMapper.selectOne(any())).thenReturn(null);
        when(encounterMapper.insert(any(PlayerNpcEncounter.class))).thenReturn(1);

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0);

        var effectsApplied = (java.util.Map<?, ?>) result.get("effectsApplied");
        assertTrue(((java.util.List<?>) effectsApplied.get("applied")).isEmpty());
        assertEquals(1, ((java.util.List<?>) effectsApplied.get("errors")).size());
        assertEquals(originalStats, player.getStatsJson());
        verify(playerMapper, never()).updateById(player);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(playerLogService).addLog(eq(1L), eq("story"), message.capture(), anyMap());
        assertFalse(message.getValue().contains("属性变化：defense"));
    }

    @Test
    void npcHpGainBeyondIntRangeIsRejectedInsteadOfWrappingToZero() throws Exception {
        NpcGhost ghost = ghost(false);
        ghost.setDialogueTreeJson("""
                [{"text":"第一段","choices":[{"text":"继续","response":"测试响应。","effects":{
                  "statChange":{"hp":1}
                }}]}]
                """);
        Player player = player();
        String originalStats = "{\"hp\":2147483647,\"maxHp\":100}";
        player.setStatsJson(originalStats);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(0L);
        when(encounterMapper.selectOne(any())).thenReturn(null);
        when(encounterMapper.insert(any(PlayerNpcEncounter.class))).thenReturn(1);

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0);

        var effectsApplied = (java.util.Map<?, ?>) result.get("effectsApplied");
        assertTrue(((java.util.List<?>) effectsApplied.get("applied")).isEmpty());
        assertEquals(1, ((java.util.List<?>) effectsApplied.get("errors")).size());
        assertEquals(originalStats, player.getStatsJson());
        verify(playerMapper, never()).updateById(player);
    }

    @Test
    void finishedUniqueDialogueCannotBeReplayed() {
        NpcGhost ghost = ghost(true);
        PlayerNpcEncounter previous = new PlayerNpcEncounter();
        previous.setPlayerId(1L);
        previous.setGhostKey("reader_ghost");
        previous.setChoiceMade("[NARRATIVE node=1 choice=0 next=-1] 离开");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player());
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost);
        when(encounterMapper.selectCount(any())).thenReturn(2L);
        when(encounterMapper.selectOne(any())).thenReturn(previous);

        var result = narrativeService.processNpcGhostEncounter(1L, "reader_ghost", 0, 0);

        assertEquals("dialogue_completed", result.get("error"));
        verify(encounterMapper, never()).insert(any(PlayerNpcEncounter.class));
        verify(inventoryService, never()).addItemWithResult(1L, "dream_shard", 1);
    }

    @Test
    void ghostCheckResumesAtThePersistedBranchInsteadOfCountingChoices() {
        PlayerNpcEncounter previous = new PlayerNpcEncounter();
        previous.setPlayerId(1L);
        previous.setGhostKey("reader_ghost");
        previous.setChoiceMade("[NARRATIVE node=0 choice=0 next=1] 拿走碎片");
        when(playerMapper.selectById(1L)).thenReturn(player());
        when(npcGhostMapper.selectList(any())).thenReturn(java.util.List.of(ghost(false)));
        when(encounterMapper.selectCount(any())).thenReturn(1L);
        when(encounterMapper.selectOne(any())).thenReturn(previous);

        var encounter = narrativeService.checkNpcGhostEncounter(1L, "ruined_station");

        assertEquals(1, encounter.get("currentNodeIndex"));
    }

    @Test
    void unfinishedDialogueTakesPriorityOverOtherRandomGhosts() {
        NpcGhost unrelated = ghost(false);
        unrelated.setGhostKey("unrelated_ghost");
        NpcGhost active = ghost(false);
        PlayerNpcEncounter previous = new PlayerNpcEncounter();
        previous.setPlayerId(1L);
        previous.setGhostKey("reader_ghost");
        previous.setChoiceMade("[NARRATIVE node=0 choice=0 next=1] 拿走碎片");
        when(playerMapper.selectById(1L)).thenReturn(player());
        when(npcGhostMapper.selectList(any())).thenReturn(java.util.List.of(unrelated, active));
        when(encounterMapper.selectOne(any())).thenReturn(null, previous);
        when(encounterMapper.selectCount(any())).thenReturn(1L);

        var encounter = narrativeService.checkNpcGhostEncounter(1L, "ruined_station");

        assertEquals("reader_ghost", encounter.get("ghostKey"));
        assertEquals(1, encounter.get("currentNodeIndex"));
    }

    @Test
    void encounterHistoryHidesInternalProgressMarker() {
        PlayerNpcEncounter previous = new PlayerNpcEncounter();
        previous.setId(4L);
        previous.setPlayerId(1L);
        previous.setGhostKey("reader_ghost");
        previous.setChoiceMade("[NARRATIVE node=0 choice=0 next=1] 拿走碎片");
        previous.setOutcome("他把碎片递给你。");
        when(encounterMapper.selectList(any())).thenReturn(java.util.List.of(previous));
        when(npcGhostMapper.selectOne(any())).thenReturn(ghost(false));

        var history = narrativeService.getPlayerEncounters(1L, 20);

        assertEquals("拿走碎片", history.getFirst().get("choiceMade"));
        assertNull(history.getFirst().get("unrelated"));
    }

    private static Player player() {
        Player player = new Player();
        player.setId(1L);
        player.setStatsJson("{}");
        return player;
    }

    private static NpcGhost ghost(boolean unique) {
        NpcGhost ghost = new NpcGhost();
        ghost.setGhostKey("reader_ghost");
        ghost.setName("白发读者");
        ghost.setDescription("故事开端");
        ghost.setEncounterWeight(0.2);
        ghost.setIsUnique(unique ? 1 : 0);
        ghost.setDialogueTreeJson("""
                [
                  {"text":"第一段","choices":[{"text":"拿走碎片","response":"他把碎片递给你。","effects":{"addItem":"dream_shard"},"leadToIndex":1}]},
                  {"text":"第二段","choices":[{"text":"离开","response":"残影消散。","leadToIndex":null}]}
                ]
                """);
        return ghost;
    }
}
