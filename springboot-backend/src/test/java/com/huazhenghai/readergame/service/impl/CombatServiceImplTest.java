package com.huazhenghai.readergame.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 战斗系统单元测试：位格修正、伤害结算与战斗记录。
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceImplTest {

    @Mock private MonsterService monsterService;
    @Mock private PlayerMapper playerMapper;
    @Mock private AvatarRankConfigMapper avatarRankConfigMapper;
    @Mock private BattleLogMapper battleLogMapper;
    @Mock private LocationMapper locationMapper;
    @Mock private RecoveryService recoveryService;
    @Mock private PlayerLogService playerLogService;
    @Mock private TitleService titleService;
    @Mock private EquipmentService equipmentService;
    @Mock private SkillService skillService;
    @Mock private InventoryService inventoryService;
    @Mock private BroadcastService broadcastService;
    @Mock private FactionService factionService;
    @Mock private QuestService questService;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private CombatServiceImpl combatService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlayerMaxHpProjectionService maxHpProjectionService;

    @BeforeEach
    void setUp() {
        maxHpProjectionService = new PlayerMaxHpProjectionService(avatarRankConfigMapper, objectMapper);
        combatService = new CombatServiceImpl(
                monsterService, playerMapper, maxHpProjectionService, battleLogMapper,
                locationMapper,
                recoveryService, playerLogService,
                titleService, equipmentService, skillService,
                inventoryService, broadcastService,
                factionService, questService,
                objectMapper, playerEffectService, resourceTransactionService);
    }

    @Test
    void fleeValidatesPlayerAndPersistsReadableCombatLog() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(Map.of("hp", 75, "maxHp", 100));
        Monster monster = new Monster();
        monster.setMonsterKey("station_rat");
        monster.setName("变异站鼠");
        monster.setEnabled(1);
        when(monsterService.getMonsterEntity("station_rat")).thenReturn(monster);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(1);

        Map<String, Object> response = combatService.resolveCombat(
                1L, "station_rat", "flee", "flee-request-1", 9L);

        assertEquals(true, response.get("success"));
        assertEquals("你从变异站鼠面前撤退了。", response.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> battle = (Map<String, Object>) response.get("battle");
        assertEquals("escape", battle.get("result"));
        assertEquals(75, battle.get("playerHpRemaining"));
        verify(recoveryService).assertCanAct(player);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("battle"), eq("你从变异站鼠面前撤退了。"), metadata.capture());
        assertEquals("combat_result", metadata.getValue().get("kind"));
        assertEquals("escape", metadata.getValue().get("result"));
        assertEquals(0, metadata.getValue().get("totalRounds"));
        ArgumentCaptor<BattleLog> savedLog = ArgumentCaptor.forClass(BattleLog.class);
        verify(battleLogMapper).insert(savedLog.capture());
        assertEquals("flee-request-1", savedLog.getValue().getRequestKey());
        assertEquals("flee", savedLog.getValue().getRequestAction());
        assertTrue(savedLog.getValue().getResultJson().contains("从变异站鼠面前撤退了"));
    }

    @Test
    void fleeRejectsPlayerOwnedByAnotherUser() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        assertThrows(BusinessException.class,
                () -> combatService.resolveCombat(1L, "station_rat", "flee", 10L));

        verifyNoInteractions(recoveryService, monsterService, playerLogService);
    }

    @Test
    void failedFleeReceiptInsertDoesNotReturnSuccess() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(Map.of("hp", 75, "maxHp", 100));
        Monster monster = new Monster();
        monster.setMonsterKey("station_rat");
        monster.setName("变异站鼠");
        monster.setEnabled(1);
        when(monsterService.getMonsterEntity("station_rat")).thenReturn(monster);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> combatService.resolveCombat(1L, "station_rat", "flee", "flee-write-failure", 9L));
    }

    @Test
    void repeatedFleeReplaysSavedResponseWithoutRecoveryOrDuplicateLogs() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        BattleLog saved = new BattleLog();
        saved.setPlayerId(1L);
        saved.setMonsterKey("station_rat");
        saved.setRequestKey("flee-retry");
        saved.setRequestAction("flee");
        saved.setResultJson("{\"success\":true,\"message\":\"已撤退\",\"battle\":{\"result\":\"escape\"}}");
        when(battleLogMapper.selectByPlayerAndRequestKey(1L, "flee-retry")).thenReturn(saved);

        Map<String, Object> response = combatService.resolveCombat(
                1L, "station_rat", "flee", "flee-retry", 9L);

        assertEquals("已撤退", response.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> battle = (Map<String, Object>) response.get("battle");
        assertEquals("escape", battle.get("result"));
        verifyNoInteractions(recoveryService, monsterService, playerLogService);
        verify(battleLogMapper, never()).insert(any(BattleLog.class));
    }

    @Test
    void growthBonusesFromEquipmentSkillsAndFactionFlowIntoCombatPower() {
        Player player = new Player();
        player.setId(1L);
        player.setStatsJson("{\"level\":2,\"maxHp\":100,\"hp\":90,\"attack\":10,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.05,\"critDamage\":1.5}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(titleService.calculateTitleEffects(1L)).thenReturn(Map.of("attack", 1));
        when(equipmentService.calculateEquipmentBonus(1L))
                .thenReturn(Map.of("attack", 5, "maxHp", 10, "critRate", 0.1));
        when(skillService.calculateSkillBonus(1L)).thenReturn(Map.of("attack", 3, "maxHp", 5));
        when(factionService.getFactionBuff(1L)).thenReturn(Map.of("buffAtk", 2));

        CombatStatsVO combatPower = combatService.calculateCombatPower(1L);

        assertEquals(23, combatPower.getAttack());
        assertEquals(115, combatPower.getMaxHp());
        assertEquals(0.15, combatPower.getCritRate(), 0.0001);
        assertEquals(Map.of("attack", 10, "defense", 5, "speed", 10, "maxHp", 100,
                        "critRate", 0.05, "critDamage", 1.5),
                combatPower.getBonuses().get("base"));
        assertEquals(Map.of("attack", 2, "defense", 1),
                combatPower.getBonuses().get("levelGrowth"));
        assertEquals(Map.of("attack", 5, "maxHp", 10, "critRate", 0.1),
                combatPower.getBonuses().get("equipment"));
        verify(equipmentService).calculateEquipmentBonus(1L);
        verify(skillService).calculateSkillBonus(1L);
    }

    @Test
    void legacyConstellationMaximumHpIsReconstructedWithoutDoubleCounting() {
        Player player = new Player();
        player.setId(1L);
        player.setStatsJson("{\"level\":1,\"avatarRank\":\"F\",\"maxHp\":120,"
                + "\"hp\":100,\"constellation\":\"master_of_steel\",\"attack\":10,"
                + "\"defense\":5,\"speed\":3,\"critRate\":0.05,\"critDamage\":1.5}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(avatarRankConfigMapper.selectList(any())).thenReturn(List.of());

        CombatStatsVO combatStats = combatService.calculateCombatPower(1L);

        assertEquals(120, combatStats.getMaxHp());
        assertEquals(Map.of("attack", 10, "defense", 5, "speed", 3, "maxHp", 100,
                        "critRate", 0.05, "critDamage", 1.5),
                combatStats.getBonuses().get("base"));
        assertEquals(Map.of("maxHp", 20), combatStats.getBonuses().get("constellation"));
    }

    @Test
    void combatProjectionRestoresLegacyRankAndPrestigeMaximumHpGrowth() {
        Player player = new Player();
        player.setId(1L);
        player.setStatsJson("{\"level\":1,\"avatarRank\":\"E\",\"prestigeLevel\":2,\"maxHp\":120,"
                + "\"hp\":100,\"constellation\":\"master_of_steel\",\"attack\":10,"
                + "\"defense\":5,\"speed\":3,\"critRate\":0.05,\"critDamage\":1.5}");
        AvatarRankConfig eRank = new AvatarRankConfig();
        eRank.setRankKey("E");
        eRank.setOrderNum(2);
        eRank.setRewardsJson("{\"stats\":{\"maxHp\":20}}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(avatarRankConfigMapper.selectList(any())).thenReturn(List.of(eRank));

        CombatStatsVO combatStats = combatService.calculateCombatPower(1L);

        assertEquals(150, combatStats.getMaxHp());
        @SuppressWarnings("unchecked")
        Map<String, Object> base = (Map<String, Object>) combatStats.getBonuses().get("base");
        assertEquals(130, base.get("maxHp"));
        assertEquals(Map.of("maxHp", 20), combatStats.getBonuses().get("constellation"));
    }

    @Test
    void fleeingWithKeyAlreadyUsedForFightIsRejectedBeforeAnyGameEffects() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        BattleLog saved = new BattleLog();
        saved.setPlayerId(1L);
        saved.setMonsterKey("station_rat");
        saved.setRequestKey("shared-key");
        saved.setRequestAction("fight");
        saved.setResultJson("{}");
        when(battleLogMapper.selectByPlayerAndRequestKey(1L, "shared-key")).thenReturn(saved);

        assertThrows(BusinessException.class,
                () -> combatService.resolveCombat(1L, "station_rat", "flee", "shared-key", 9L));

        verifyNoInteractions(recoveryService, monsterService, playerLogService, questService);
    }

    @Test
    void reusedRequestKeyWithDifferentActionOrMonsterIsRejected() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        BattleLog saved = new BattleLog();
        saved.setPlayerId(1L);
        saved.setMonsterKey("another_monster");
        saved.setRequestKey("already-used");
        saved.setRequestAction("fight");
        saved.setResultJson("{}");
        when(battleLogMapper.selectByPlayerAndRequestKey(1L, "already-used")).thenReturn(saved);

        assertThrows(BusinessException.class,
                () -> combatService.startCombat(1L, "station_rat", "already-used", 9L));

        verifyNoInteractions(recoveryService, monsterService, playerLogService, questService);
    }

    @Test
    void repeatedFightReplaysSavedCombatResultWithoutSimulatingOrGrantingAgain() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        CombatResultVO savedResult = new CombatResultVO();
        savedResult.setResult("win");
        savedResult.setTotalRounds(3);
        savedResult.setPlayerHpBefore(80);
        savedResult.setPlayerHpAfter(42);
        BattleLog saved = new BattleLog();
        saved.setPlayerId(1L);
        saved.setMonsterKey("station_rat");
        saved.setRequestKey("fight-retry");
        saved.setRequestAction("fight");
        saved.setResultJson(objectMapper.writeValueAsString(savedResult));
        when(battleLogMapper.selectByPlayerAndRequestKey(1L, "fight-retry")).thenReturn(saved);

        CombatResultVO replay = combatService.startCombat(1L, "station_rat", "fight-retry", 9L);

        assertEquals("win", replay.getResult());
        assertEquals(3, replay.getTotalRounds());
        assertEquals(42, replay.getPlayerHpAfter());
        verifyNoInteractions(recoveryService, monsterService, playerLogService,
                questService, factionService, broadcastService, inventoryService);
        verify(battleLogMapper, never()).insert(any(BattleLog.class));
    }

    @Test
    void failedPlayerSettlementWriteDoesNotPersistBattleReceipt() throws Exception {
        Player player = prepareWinningFight();
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> combatService.startCombat(1L, "weak_target", "player-write-failure", 9L));

        verify(battleLogMapper, never()).insert(any(BattleLog.class));
    }

    @Test
    void failedBattleReceiptInsertDoesNotReturnCombatSuccess() throws Exception {
        Player player = prepareWinningFight();
        when(playerMapper.updateById(player)).thenReturn(1);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> combatService.startCombat(1L, "weak_target", "receipt-insert-failure", 9L));
    }

    @Test
    void failedBattleHistorySerializationDoesNotPersistAnIncompleteReceipt() throws Exception {
        Player player = prepareWinningFight();
        when(playerMapper.updateById(player)).thenReturn(1);
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                if (value instanceof List<?> list && !list.isEmpty()
                        && list.get(0) instanceof Map<?, ?> first && first.containsKey("round")) {
                    throw new JsonProcessingException("模拟战斗回合快照序列化失败") {};
                }
                return super.writeValueAsString(value);
            }
        };
        combatService = new CombatServiceImpl(
                monsterService, playerMapper, maxHpProjectionService, battleLogMapper, locationMapper,
                recoveryService, playerLogService, titleService, equipmentService, skillService,
                inventoryService, broadcastService, factionService, questService,
                failingMapper, playerEffectService, resourceTransactionService);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> combatService.startCombat(1L, "weak_target", "history-serialization-failure", 9L));

        assertEquals("战斗历史记录序列化失败", error.getMessage());
        verify(battleLogMapper, never()).insert(any(BattleLog.class));
    }

    @Test
    void failedResolvedCombatReceiptUpdateDoesNotReturnSuccess() throws Exception {
        Player player = prepareWinningFight();
        when(playerMapper.updateById(player)).thenReturn(1);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(1);
        BattleLog receipt = new BattleLog();
        when(battleLogMapper.selectByPlayerAndRequestKey(1L, "receipt-update-failure"))
                .thenReturn(null, null, receipt);
        when(battleLogMapper.updateById(receipt)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> combatService.resolveCombat(
                1L, "weak_target", "attack", "receipt-update-failure", 9L));
    }

    @Test
    void combatDropReceiptContainsOnlyItemsAndEquipmentActuallyGranted() throws Exception {
        when(inventoryService.addItemWithResult(1L, "herb", 3))
                .thenReturn(new ItemGrantResult(2, 7, "草药"));
        when(inventoryService.addItemWithResult(1L, "full_stack", 1))
                .thenReturn(new ItemGrantResult(0, 10));
        when(equipmentService.addEquipmentWithResult(1L, "worn_blade", "combat"))
                .thenReturn(new EquipmentGrantResult(1, "磨损短刃"));
        when(equipmentService.addEquipmentWithResult(1L, "duplicate_blade", "combat"))
                .thenReturn(new EquipmentGrantResult(0, null));

        Map<String, Object> drops = new LinkedHashMap<>();
        drops.put("items", List.of(
                Map.of("itemKey", "herb", "quantity", 3, "dropRate", 1.0),
                Map.of("itemKey", "full_stack", "quantity", 1, "dropRate", 1.0)));
        drops.put("equipment", List.of(
                Map.of("equipmentKey", "worn_blade", "dropRate", 1.0),
                Map.of("equipmentKey", "duplicate_blade", "dropRate", 1.0)));

        List<Map<String, Object>> acquired = invokeApplyMonsterDrops(1L, drops);

        assertEquals(2, acquired.size());
        assertEquals("herb", acquired.get(0).get("itemKey"));
        assertEquals("草药", acquired.get(0).get("itemName"));
        assertEquals(2, acquired.get(0).get("quantity"));
        assertEquals(7, acquired.get(0).get("totalQuantity"));
        assertEquals("worn_blade", acquired.get(1).get("equipmentKey"));
        assertEquals("磨损短刃", acquired.get(1).get("equipmentName"));
    }

    @Test
    void combatResponsePlayerLogAndBattleHistoryShareTheSameGrantedRewardsAndDrops() throws Exception {
        Player player = prepareWinningFight();
        Monster monster = new Monster();
        monster.setMonsterKey("weak_target");
        monster.setName("测试目标");
        monster.setType("test");
        monster.setRarity("common");
        monster.setLocationKey("test_location");
        monster.setStatsJson("{\"hp\":1,\"attack\":0,\"defense\":0,\"speed\":1}");
        monster.setRewardsJson("{\"exp\":10,\"coins\":5,\"storyFragments\":2}");
        monster.setDropsJson("{\"items\":[{\"itemKey\":\"herb\",\"quantity\":3,\"dropRate\":1.0}],"
                + "\"equipment\":[{\"equipmentKey\":\"worn_blade\",\"dropRate\":1.0}]}");
        monster.setNarrativeTagsJson("[\"abyss\",\"corrupted\"]");
        monster.setEnabled(1);
        when(monsterService.getMonsterEntity("weak_target")).thenReturn(monster);
        when(inventoryService.addItemWithResult(1L, "herb", 3))
                .thenReturn(new ItemGrantResult(2, 7, "草药"));
        when(equipmentService.addEquipmentWithResult(1L, "worn_blade", "combat"))
                .thenReturn(new EquipmentGrantResult(1, "磨损短刃"));
        when(playerMapper.updateById(player)).thenReturn(1);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(1);

        CombatResultVO response = combatService.startCombat(
                1L, "weak_target", "shared-settlement-receipt", 9L);

        assertEquals("win", response.getResult());
        Map<String, Object> playerAction = response.getRounds().get(0).getActions().get(0);
        assertFalse(playerAction.containsKey("suppressionNote"),
                "怪物叙事标签本身不能触发固定伤害克制");
        verify(titleService, never()).getEquippedTitle(1L);
        assertEquals(15, player.getCoins());
        assertEquals(2, player.getStoryFragments());
        assertEquals(10, response.getRewards().getExp());
        assertEquals(5, response.getRewards().getCoins());
        assertEquals(2, response.getRewards().getStoryFragments());
        verify(questService).addProgress(1L, "combat_win_count", 1, "combat", "weak_target");
        assertEquals(List.of(
                Map.of("itemKey", "herb", "itemName", "草药", "quantity", 2, "totalQuantity", 7),
                Map.of("equipmentKey", "worn_blade", "equipmentName", "磨损短刃")), response.getDrops());

        ArgumentCaptor<Map<String, Object>> logMetadata = ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(1L), eq("battle"),
                eq("击败了 测试目标（1回合）"), logMetadata.capture());
        assertEquals("combat_result", logMetadata.getValue().get("kind"));
        assertEquals(objectMapper.valueToTree(response.getRewards()),
                objectMapper.valueToTree(logMetadata.getValue().get("rewards")));
        assertEquals(objectMapper.valueToTree(response.getDrops()),
                objectMapper.valueToTree(logMetadata.getValue().get("drops")));
        assertEquals(objectMapper.valueToTree(response.getNewLogs()),
                objectMapper.valueToTree(logMetadata.getValue().get("newLogs")));

        ArgumentCaptor<BattleLog> savedLog = ArgumentCaptor.forClass(BattleLog.class);
        verify(battleLogMapper).insert(savedLog.capture());
        assertEquals("shared-settlement-receipt", savedLog.getValue().getRequestKey());
        assertEquals(objectMapper.valueToTree(response.getRewards()),
                objectMapper.readTree(savedLog.getValue().getRewardsJson()));
        assertEquals(objectMapper.valueToTree(response.getDrops()),
                objectMapper.readTree(savedLog.getValue().getDropsJson()));
        assertEquals(objectMapper.valueToTree(response.getRounds()),
                objectMapper.readTree(savedLog.getValue().getRoundsJson()));
        assertEquals(objectMapper.valueToTree(response.getRewards()),
                objectMapper.readTree(savedLog.getValue().getResultJson()).get("rewards"));
        assertEquals(objectMapper.valueToTree(response.getDrops()),
                objectMapper.readTree(savedLog.getValue().getResultJson()).get("drops"));
    }

    @Test
    void losingCombatDoesNotAdvanceTheDailyCombatVictoryQuest() throws Exception {
        Player player = prepareWinningFight();
        Monster monster = monsterService.getMonsterEntity("weak_target");
        monster.setStatsJson("{\"hp\":10000,\"attack\":1000,\"defense\":0,\"speed\":1000}");
        when(playerMapper.updateById(player)).thenReturn(1);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(1);

        CombatResultVO response = combatService.startCombat(
                1L, "weak_target", "daily-quest-loss", 9L);

        assertEquals("lose", response.getResult());
        verify(questService, never()).addProgress(
                eq(1L), eq("combat_win_count"), anyInt(), anyString(), anyString());
    }

    @Test
    void personalBridgeClueMitigatesOnlyTheFirstHitFromItsMatchingStoryEater() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("测试角色");
        player.setCoins(0);
        player.setStoryFragments(0);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("level", 1);
        stats.put("maxHp", 100);
        stats.put("hp", 100);
        stats.put("attack", 100);
        stats.put("defense", 5);
        stats.put("speed", 10);
        stats.put("critRate", 0.0);
        stats.put("critDamage", 1.5);
        stats.put("exp", 0);
        stats.put("avatarRank", "F");
        player.setStatsJson(objectMapper.writeValueAsString(stats));
        player.setStageProgressJson("{\"sideEventsTriggered\":[\"gwangjin_side_boat\"]}");

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);
        when(titleService.calculateTitleEffects(1L)).thenReturn(Collections.emptyMap());
        when(equipmentService.calculateEquipmentBonus(1L)).thenReturn(Collections.emptyMap());
        when(skillService.calculateSkillBonus(1L)).thenReturn(Collections.emptyMap());
        when(factionService.getFactionBuff(1L)).thenReturn(Collections.emptyMap());

        Monster storyEater = new Monster();
        storyEater.setMonsterKey("bridge_hunger_entity");
        storyEater.setName("桥下的饥饿实体");
        storyEater.setType("normal");
        storyEater.setRarity("common");
        storyEater.setLocationKey("gwangjin_bridge");
        storyEater.setStatsJson("{\"hp\":300,\"attack\":18,\"defense\":0,\"speed\":20}");
        storyEater.setRewardsJson("{}");
        storyEater.setDropsJson("{}");
        storyEater.setEnabled(1);
        when(monsterService.getMonsterEntity("bridge_hunger_entity")).thenReturn(storyEater);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(battleLogMapper.insert(any(BattleLog.class))).thenReturn(1);

        CombatResultVO response = combatService.startCombat(
                1L, "bridge_hunger_entity", "boatman-story-anchor", 9L);

        assertEquals("win", response.getResult());
        List<Map<String, Object>> monsterActions = response.getRounds().stream()
                .flatMap(round -> round.getActions().stream())
                .filter(action -> "monster".equals(action.get("actor")))
                .toList();
        assertTrue(monsterActions.size() > 1);
        assertEquals(12, monsterActions.get(0).get("damage"));
        assertTrue(((String) monsterActions.get(0).get("suppressionNote")).contains("减伤 4 点"));
        assertEquals(16, monsterActions.get(1).get("damage"));
        assertFalse(monsterActions.get(1).containsKey("suppressionNote"));

        @SuppressWarnings("unchecked")
        Map<String, Object> relation = (Map<String, Object>) monsterActions.get(0).get("storyRelation");
        assertEquals("gwangjin_boatman_story_anchor", relation.get("ruleKey"));
        assertEquals("game_derived", relation.get("sourceCategory"));
        assertEquals("gwangjin_side_boat", relation.get("evidenceEventKey"));
        assertEquals(4, relation.get("damageReduced"));
    }

    @Test
    void combatRejectsCoinOverflowBeforePersistingTheSettlement() throws Exception {
        Player player = prepareWinningFight();
        player.setCoins(Integer.MAX_VALUE);

        BusinessException error = assertThrows(BusinessException.class,
                () -> combatService.startCombat(1L, "weak_target", "coin-overflow", 9L));

        assertEquals("星币已达到可保存上限，暂时无法领取", error.getMessage());
        assertEquals(Integer.MAX_VALUE, player.getCoins());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(battleLogMapper, never()).insert(any(BattleLog.class));
    }

    @Test
    void combatRejectsChannelHeatOverflowBeforePersistingTheSettlement() throws Exception {
        Player player = prepareWinningFight();
        Map<String, Object> stats = recoveryService.applyRecovery(player);
        stats.put("channelHeat", Integer.MAX_VALUE);
        player.setStatsJson(objectMapper.writeValueAsString(stats));
        Monster monster = monsterService.getMonsterEntity("weak_target");
        monster.setRewardsJson("{\"exp\":10,\"coins\":5,\"channelHeat\":1}");
        when(playerEffectService.applyChannelHeatGainRate(1L, 1)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> combatService.startCombat(1L, "weak_target", "channel-heat-overflow", 9L));

        assertEquals("星流热度超出可保存范围，操作无法完成", error.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(battleLogMapper, never()).insert(any(BattleLog.class));
    }

    // ═══════════════════════════════════════════════════════════════
    // 位格压制
    // ═══════════════════════════════════════════════════════════════

    @Test
    void rankToValue_Fis1() throws Exception {
        assertEquals(1, invokeRankToValue("F"));
    }

    @Test
    void rankToValue_Dis3() throws Exception {
        assertEquals(3, invokeRankToValue("D"));
    }

    @Test
    void rankToValue_SSSis9() throws Exception {
        assertEquals(9, invokeRankToValue("SSS"));
    }

    @Test
    void rankToValue_nullDefaultsTo1() throws Exception {
        assertEquals(1, invokeRankToValue(null));
    }

    @Test
    void rankToValue_unknownDefaultsTo1() throws Exception {
        assertEquals(1, invokeRankToValue("UNKNOWN"));
    }

    @Test
    void rankSuppressionAdvantage() throws Exception {
        // F(1) vs F(1) → diff 0 → mult 1.0
        double mult = invokeRankSuppression("F", "F");
        assertEquals(1.0, mult, 0.001);

        // D(3) vs F(1) → diff 2 → 1.0 + 2*0.15 = 1.3
        mult = invokeRankSuppression("D", "F");
        assertEquals(1.3, mult, 0.001);

        // S(7) vs F(1) → diff 6 → 1.0 + 6*0.15 = 1.9
        mult = invokeRankSuppression("S", "F");
        assertEquals(1.9, mult, 0.001);
    }

    @Test
    void rankSuppressionDisadvantage() throws Exception {
        // F(1) vs D(3) → diff -2 → 1.0 + (-2)*0.15 = 0.7
        double mult = invokeRankSuppression("F", "D");
        assertEquals(0.7, mult, 0.001);

        // F(1) vs S(7) → diff -6 → 1.0 + (-6)*0.15 = 0.1, clamped to 0.5
        mult = invokeRankSuppression("F", "S");
        assertEquals(0.5, mult, 0.001);
    }

    @Test
    void rankSuppressionAcrossFullRankRange() throws Exception {
        // The current ladder spans 8 steps: F vs SSS bottoms at 0.5 and SSS vs F reaches 2.2.
        double mult = invokeRankSuppression("F", "SSS");
        assertEquals(0.5, mult, 0.001);

        // SSS(9) vs F(1) → diff 8 → 1.0 + 8*0.15 = 2.2 (under 3.0)
        mult = invokeRankSuppression("SSS", "F");
        assertEquals(2.2, mult, 0.001);
    }

    @Test
    void rankSuppressionCappedAt05() throws Exception {
        // Worst case: F vs SSS = clamp(-0.2, 0.5, 3.0) = 0.5
        double mult = invokeRankSuppression("F", "SSS");
        assertEquals(0.5, mult, 0.001);
    }

    // ═══════════════════════════════════════════════════════════════
    // 伤害计算（含压制倍率）
    // ═══════════════════════════════════════════════════════════════

    @Test
    void calcDamageBaseFormula() throws Exception {
        // atk=10, def=5 → baseDmg = max(1, round(10 - 5*0.5)) = 8
        // no crit, mult=1.0
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.0, "");
        assertEquals(8.0, ((Number) result.get("damage")).doubleValue(), 0.001);
        assertEquals(false, result.get("critical"));
    }

    @Test
    void calcDamageWithSuppressionMult() throws Exception {
        // atk=10, def=5 → base=8, mult=1.4 → 11
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.4, "");
        assertEquals(11.0, ((Number) result.get("damage")).doubleValue(), 0.001);
    }

    @Test
    void calcDamageWithSuppressionNote() throws Exception {
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.0, "位格压制");
        assertTrue(result.containsKey("suppressionNote"));
        assertEquals("位格压制", result.get("suppressionNote"));
    }

    @Test
    void calcDamageSuppressionNoteEmptyWhenEmptyString() throws Exception {
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.0, "");
        assertFalse(result.containsKey("suppressionNote"));
    }

    @Test
    void calcDamageMinimum1() throws Exception {
        // atk=1, def=100 → base = max(1, 1 - 50) = 1, mult=0.6 → 0.6, max(1, ...) = 1
        Map<String, Object> result = invokeCalcDamage(1, 100, 0.0, 1.5, 0.6, "");
        double dmg = ((Number) result.get("damage")).doubleValue();
        assertTrue(dmg >= 1.0, "damage should never be below 1, got " + dmg);
    }

    @Test
    void calcDamageCriticalHit() throws Exception {
        // atk=20, def=0 → base=20, critRate=1.0 (always), critDamage=2.0 → 40*1.0=40
        Map<String, Object> result = invokeCalcDamage(20, 0, 1.0, 2.0, 1.0, "");
        assertEquals(true, result.get("critical"));
        assertEquals(40.0, ((Number) result.get("damage")).doubleValue(), 0.001);
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON 解析辅助
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parseStringListValidJson() throws Exception {
        List<String> result = invokeParseStringList("[\"abyss\",\"corrupted\"]");
        assertEquals(2, result.size());
        assertEquals("abyss", result.get(0));
        assertEquals("corrupted", result.get(1));
    }

    @Test
    void parseStringListNullReturnsEmpty() throws Exception {
        List<String> result = invokeParseStringList(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseStringListEmptyReturnsEmpty() throws Exception {
        List<String> result = invokeParseStringList("");
        assertTrue(result.isEmpty());
        result = invokeParseStringList("null");
        assertTrue(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Reflection helpers
    // ═══════════════════════════════════════════════════════════════

    private int invokeRankToValue(String rank) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod("rankToValue", String.class);
        m.setAccessible(true);
        return (int) m.invoke(combatService, rank);
    }

    private double invokeRankSuppression(String playerRank, String monsterRank) throws Exception {
        int playerVal = invokeRankToValue(playerRank);
        int monsterVal = invokeRankToValue(monsterRank);
        int diff = playerVal - monsterVal;
        return Math.max(0.5, Math.min(3.0, 1.0 + diff * 0.15));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeCalcDamage(
            int atk, int def, double critRate, double critDamage,
            double suppressionMult, String suppressionNote) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod(
                "calcDamage", int.class, int.class, double.class, double.class,
                double.class, String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(
                combatService, atk, def, critRate, critDamage,
                suppressionMult, suppressionNote);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeParseStringList(String json) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod("parseStringList", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(combatService, json);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeApplyMonsterDrops(Long playerId,
                                                               Map<String, Object> drops) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod(
                "applyMonsterDrops", Long.class, String.class, String.class, Map.class);
        m.setAccessible(true);
        return (List<Map<String, Object>>) m.invoke(
                combatService, playerId, "test_monster", "drop-reflection-test", drops);
    }

    private Player prepareWinningFight() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setPlayerName("测试角色");
        player.setCoins(10);
        player.setStoryFragments(0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("level", 1);
        stats.put("maxHp", 100);
        stats.put("hp", 100);
        stats.put("attack", 1000);
        stats.put("defense", 5);
        stats.put("speed", 10);
        stats.put("critRate", 0.0);
        stats.put("critDamage", 1.5);
        stats.put("exp", 0);
        stats.put("avatarRank", "F");
        player.setStatsJson(objectMapper.writeValueAsString(stats));

        Monster monster = new Monster();
        monster.setMonsterKey("weak_target");
        monster.setName("测试目标");
        monster.setType("test");
        monster.setRarity("common");
        monster.setLocationKey("test_location");
        monster.setStatsJson("{\"hp\":1,\"attack\":0,\"defense\":0,\"speed\":1}");
        monster.setRewardsJson("{\"exp\":10,\"coins\":5}");
        monster.setDropsJson("{}");
        monster.setNarrativeTagsJson("[]");
        monster.setEnabled(1);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(playerMapper.selectById(1L)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(stats);
        when(monsterService.getMonsterEntity("weak_target")).thenReturn(monster);
        when(titleService.calculateTitleEffects(1L)).thenReturn(Collections.emptyMap());
        when(equipmentService.calculateEquipmentBonus(1L)).thenReturn(Collections.emptyMap());
        when(skillService.calculateSkillBonus(1L)).thenReturn(Collections.emptyMap());
        when(factionService.getFactionBuff(1L)).thenReturn(Collections.emptyMap());
        return player;
    }
}
