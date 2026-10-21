package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.User;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.PlayerVO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameBootstrapServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long PLAYER_ID = 11L;

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final PlayerLogService playerLogService = mock(PlayerLogService.class);
    private final RecoveryService recoveryService = mock(RecoveryService.class);
    private final CombatService combatService = mock(CombatService.class);
    private final TitleService titleService = mock(TitleService.class);
    private final AvatarRankService avatarRankService = mock(AvatarRankService.class);
    private final ChapterService chapterService = mock(ChapterService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private final SkillService skillService = mock(SkillService.class);
    private final BattleLogMapper battleLogMapper = mock(BattleLogMapper.class);
    private final PkRecordMapper pkRecordMapper = mock(PkRecordMapper.class);
    private final RankingMapper rankingMapper = mock(RankingMapper.class);
    private final BroadcastService broadcastService = mock(BroadcastService.class);
    private final WorldlineService worldlineService = mock(WorldlineService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final OnlinePlayerService onlinePlayerService = mock(OnlinePlayerService.class);
    private final FriendService friendService = mock(FriendService.class);
    private final TradeService tradeService = mock(TradeService.class);
    private final PartyService partyService = mock(PartyService.class);
    private final WorldBossService worldBossService = mock(WorldBossService.class);
    private final FactionService factionService = mock(FactionService.class);
    private final QuestService questService = mock(QuestService.class);
    private final PlayerStaminaProjectionService staminaProjectionService = mock(PlayerStaminaProjectionService.class);

    @Test
    void bootstrapIncludesLevelGrowthAndEquippedTitleInDisplayedCombatStats() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("reader");

        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);

        Map<String, Object> persistedStats = new HashMap<>();
        persistedStats.put("level", 4);
        persistedStats.put("attack", 18);
        persistedStats.put("defense", 9);
        persistedStats.put("speed", 7);
        persistedStats.put("critRate", 0.05);
        persistedStats.put("maxHp", 150);
        persistedStats.put("hp", 100);
        persistedStats.put("allocatedAtk", 8);
        persistedStats.put("allocatedDef", 1);
        PlayerVO playerVO = PlayerVO.builder().id(PLAYER_ID).stats(persistedStats).build();

        Map<String, Object> recoveryStats = new HashMap<>();
        recoveryStats.put("isResting", false);
        recoveryStats.put("hp", 100);
        recoveryStats.put("maxHp", 150);
        recoveryStats.put("stamina", 50);
        recoveryStats.put("maxStamina", 50);
        recoveryStats.put("exp", 300);

        PlayerTitleVO title = new PlayerTitleVO();
        title.setName("最初读者");
        CombatStatsVO combatStats = new CombatStatsVO();
        combatStats.setLevel(4);
        combatStats.setHp(100);
        combatStats.setMaxHp(205);
        combatStats.setAttack(60);
        combatStats.setDefense(30);
        combatStats.setSpeed(7);
        combatStats.setCritRate(0.05);
        combatStats.setCritDamage(1.5);
        combatStats.setBonuses(Map.of(
                "base", Map.of("attack", 18, "defense", 9, "speed", 7, "maxHp", 150, "critRate", 0.05),
                "levelGrowth", Map.of("attack", 6, "defense", 3),
                "constellation", Map.of("maxHp", 20),
                "title", Map.of("attack", 25, "defense", 15, "maxHp", 20),
                "equipment", Map.of("attack", 5, "defense", 2, "maxHp", 10),
                "skill", Map.of("attack", 3, "maxHp", 5),
                "faction", Map.of("attack", 2),
                "factionSkill", Map.of("attack", 1, "defense", 1)));

        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(playerService.findByUserId(USER_ID)).thenReturn(player);
        when(playerService.getPlayer(PLAYER_ID)).thenReturn(playerVO);
        when(playerLogService.getRecentLogs(PLAYER_ID, 50)).thenReturn(List.of());
        when(recoveryService.applyRecovery(player)).thenReturn(recoveryStats);
        when(combatService.calculateCombatPower(PLAYER_ID)).thenReturn(combatStats);
        when(titleService.getEquippedTitle(PLAYER_ID)).thenReturn(title);
        when(equipmentService.getEquipmentBonusDetail(PLAYER_ID)).thenReturn(Map.of());
        when(skillService.getSkillBonusDetail(PLAYER_ID)).thenReturn(Map.of());
        when(staminaProjectionService.projectMaxStamina(PLAYER_ID, 50)).thenReturn(55);

        GameBootstrapServiceImpl service = new GameBootstrapServiceImpl(
                userMapper, playerService, playerLogService, recoveryService, combatService, titleService,
                avatarRankService, chapterService, inventoryService, equipmentService, skillService,
                battleLogMapper, pkRecordMapper, rankingMapper, broadcastService, worldlineService,
                chatService, onlinePlayerService, friendService, tradeService, partyService,
                worldBossService, factionService, questService, staminaProjectionService);

        var bootstrap = service.bootstrap(USER_ID);
        Map<String, Object> result = bootstrap.getPlayer().getStats();

        assertEquals(60.0, ((Number) result.get("attack")).doubleValue());
        assertEquals(30.0, ((Number) result.get("defense")).doubleValue());
        assertEquals(true, result.get("combatProjection"));
        assertEquals(18.0, ((Number) result.get("bonusAtkCombatBase")).doubleValue());
        assertEquals(10.0, ((Number) result.get("bonusAtkBase")).doubleValue());
        assertEquals(8.0, ((Number) result.get("bonusAtkAlloc")).doubleValue());
        assertEquals(0.0, ((Number) result.get("bonusAtkStoredGrowth")).doubleValue());
        assertEquals(6.0, ((Number) result.get("bonusAtkLevelGrowth")).doubleValue());
        assertEquals(3.0, ((Number) result.get("bonusDefLevelGrowth")).doubleValue());
        assertEquals(25.0, ((Number) result.get("bonusAtkTitle")).doubleValue());
        assertEquals(15.0, ((Number) result.get("bonusDefTitle")).doubleValue());
        assertEquals(3.0, ((Number) result.get("bonusDefStoredGrowth")).doubleValue());
        assertEquals(20.0, ((Number) result.get("bonusMaxHpTitle")).doubleValue());
        assertEquals(20.0, ((Number) result.get("bonusMaxHpConstellation")).doubleValue());
        assertEquals(5.0, ((Number) result.get("bonusAtkEquipment")).doubleValue());
        assertEquals(3.0, ((Number) result.get("bonusAtkSkill")).doubleValue());
        assertEquals(2.0, ((Number) result.get("bonusAtkFaction")).doubleValue());
        assertEquals(1.0, ((Number) result.get("bonusAtkFactionSkill")).doubleValue());
        assertEquals("最初读者", result.get("bonusTitleName"));
        assertEquals("最初读者 +25", result.get("bonusAtkTitleDetail"));
        assertEquals(55, ((Number) result.get("maxStamina")).intValue());
        assertEquals(55, bootstrap.getRestState().getMaxStamina());
        assertEquals(588, bootstrap.getRestState().getStaminaIntervalSeconds());
        verify(equipmentService, never()).calculateEquipmentBonus(PLAYER_ID);
        verify(skillService, never()).calculateSkillBonus(PLAYER_ID);
        verify(factionService, never()).getFactionBuff(PLAYER_ID);
    }

    @Test
    void bootstrapReusesPlayerReadCombatProjectionInsteadOfCalculatingItAgain() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("reader");

        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);

        Map<String, Object> projectedStats = new HashMap<>();
        projectedStats.put("level", 3);
        projectedStats.put("attack", 42);
        projectedStats.put("defense", 18);
        projectedStats.put("speed", 7);
        projectedStats.put("critRate", 0.1);
        projectedStats.put("maxHp", 160);
        projectedStats.put("hp", 100);
        projectedStats.put("combatProjection", true);
        projectedStats.put("bonusAtkTitle", 4.0);
        projectedStats.put("combatBonusSources", Map.of(
                "base", Map.of("attack", 20, "defense", 10, "speed", 7, "maxHp", 140),
                "title", Map.of("attack", 4)));
        PlayerVO playerVO = PlayerVO.builder().id(PLAYER_ID).stats(projectedStats).build();

        Map<String, Object> recoveryStats = new HashMap<>();
        recoveryStats.put("isResting", false);
        recoveryStats.put("hp", 100);
        recoveryStats.put("maxHp", 160);
        recoveryStats.put("stamina", 50);
        recoveryStats.put("maxStamina", 50);
        recoveryStats.put("exp", 200);

        PlayerTitleVO title = new PlayerTitleVO();
        title.setName("最初读者");

        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(playerService.findByUserId(USER_ID)).thenReturn(player);
        when(playerService.getPlayer(PLAYER_ID)).thenReturn(playerVO);
        when(playerLogService.getRecentLogs(PLAYER_ID, 50)).thenReturn(List.of());
        when(recoveryService.applyRecovery(player)).thenReturn(recoveryStats);
        when(titleService.getEquippedTitle(PLAYER_ID)).thenReturn(title);
        when(equipmentService.getEquipmentBonusDetail(PLAYER_ID)).thenReturn(Map.of());
        when(skillService.getSkillBonusDetail(PLAYER_ID)).thenReturn(Map.of());
        when(staminaProjectionService.projectMaxStamina(PLAYER_ID, 50)).thenReturn(50);

        GameBootstrapServiceImpl service = new GameBootstrapServiceImpl(
                userMapper, playerService, playerLogService, recoveryService, combatService, titleService,
                avatarRankService, chapterService, inventoryService, equipmentService, skillService,
                battleLogMapper, pkRecordMapper, rankingMapper, broadcastService, worldlineService,
                chatService, onlinePlayerService, friendService, tradeService, partyService,
                worldBossService, factionService, questService, staminaProjectionService);

        Map<String, Object> result = service.bootstrap(USER_ID).getPlayer().getStats();

        assertEquals(42, ((Number) result.get("attack")).intValue());
        assertEquals(true, result.get("combatProjection"));
        assertEquals(4.0, ((Number) result.get("bonusAtkTitle")).doubleValue());
        assertEquals("最初读者", result.get("bonusTitleName"));
        verify(combatService, never()).calculateCombatPower(PLAYER_ID);
    }
}
