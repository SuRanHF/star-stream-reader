package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import com.huazhenghai.readergame.vo.PlayerVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceImplTest {

    private static final Long PLAYER_ID = 41L;

    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerMaxHpProjectionService maxHpProjectionService;
    @Mock private CombatService combatService;
    @Mock private LocationMapper locationMapper;

    private PlayerServiceImpl playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerServiceImpl(playerMapper, playerLogMapper, playerLogService,
                maxHpProjectionService, combatService, locationMapper, new ObjectMapper());
    }

    @Test
    void playerReadUsesCombatTotalsAndSourcesWithoutPersistingProjectedStats() {
        String savedStats = "{\"level\":12,\"exp\":50,\"hp\":71,\"maxHp\":185,"
                + "\"maxHpConstellationBonus\":20,\"attack\":24,\"defense\":8,\"speed\":9,"
                + "\"critRate\":0.08,\"critDamage\":1.5,\"allocatedAtk\":4,\"allocatedDef\":3,"
                + "\"allocatedSpd\":2,\"allocatedCrit\":2,\"constellation\":\"master_of_steel\"}";
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setStatsJson(savedStats);

        CombatStatsVO combatStats = new CombatStatsVO();
        combatStats.setLevel(12);
        combatStats.setHp(71);
        combatStats.setMaxHp(207);
        combatStats.setAttack(80);
        combatStats.setDefense(27);
        combatStats.setSpeed(9);
        combatStats.setCritRate(0.18);
        combatStats.setCritDamage(1.5);
        combatStats.setBonuses(Map.of(
                "base", Map.of("attack", 34, "defense", 12, "speed", 9,
                        "maxHp", 165, "critRate", 0.14),
                "levelGrowth", Map.of("attack", 22, "defense", 11),
                "constellation", Map.of("maxHp", 20),
                "title", Map.of("attack", 3, "maxHp", 10, "critRate", 0.03),
                "equipment", Map.of("attack", 11, "defense", 4, "maxHp", 12, "critRate", 0.01),
                "skill", Map.of("attack", 7),
                "faction", Map.of("attack", 1),
                "factionSkill", Map.of("attack", 2)));

        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(playerLogService.getRecentLogs(PLAYER_ID, 50)).thenReturn(List.of());
        when(maxHpProjectionService.projectForRead(any())).thenReturn(185);
        when(combatService.calculateCombatPower(PLAYER_ID)).thenReturn(combatStats);

        PlayerVO result = playerService.getPlayer(PLAYER_ID);
        Map<String, Object> stats = result.getStats();

        assertEquals(12, ((Number) stats.get("level")).intValue());
        assertEquals(71, ((Number) stats.get("hp")).intValue());
        assertEquals(207, ((Number) stats.get("maxHp")).intValue());
        assertEquals(80, ((Number) stats.get("attack")).intValue());
        assertEquals(27, ((Number) stats.get("defense")).intValue());
        assertEquals(9, ((Number) stats.get("speed")).intValue());
        assertEquals(0.18, ((Number) stats.get("critRate")).doubleValue(), 0.0001);
        assertEquals(true, stats.get("combatProjection"));
        assertEquals(20.0, ((Number) stats.get("bonusAtkStoredGrowth")).doubleValue());
        assertEquals(22.0, ((Number) stats.get("bonusAtkLevelGrowth")).doubleValue());
        assertEquals(11.0, ((Number) stats.get("bonusAtkEquipment")).doubleValue());
        assertEquals(2.0, ((Number) stats.get("bonusAtkFactionSkill")).doubleValue());
        assertEquals(8.0, ((Number) stats.get("bonusDefConstellation")).doubleValue(),
                "non-combat constellation effects must remain visible with their explanatory tooltip");
        assertEquals(savedStats, player.getStatsJson());
        verify(combatService).calculateCombatPower(PLAYER_ID);
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerMapper, never()).insert(any(Player.class));
    }
}
