package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.AvatarRankConfig;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.service.PlayerStaminaProjectionService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private AvatarRankConfigMapper rankConfigMapper;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private PlayerStaminaProjectionService staminaProjectionService;

    private RecoveryServiceImpl recoveryService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PlayerMaxHpProjectionService maxHpProjectionService =
                new PlayerMaxHpProjectionService(rankConfigMapper, objectMapper);
        recoveryService = new RecoveryServiceImpl(playerMapper, playerLogService,
                maxHpProjectionService, staminaProjectionService,
                resourceTransactionService, objectMapper);
    }

    @Test
    void passiveExperienceCrossingLevelThresholdImmediatelyGrantsLevelRewards() {
        when(resourceTransactionService.record(any(PlayerResourceTransactionService.Change.class))).thenReturn(true);
        Player player = new Player();
        player.setId(7L);
        long lastRecoveryAt = System.currentTimeMillis() - 90_000L;
        player.setStatsJson("{\"hp\":80,\"maxHp\":100,\"stamina\":50,\"maxStamina\":50,"
                + "\"exp\":99,\"level\":1,\"freePoints\":4,\"isResting\":true,"
                + "\"lastRecoveryAt\":" + lastRecoveryAt + "}");
        when(playerMapper.selectByIdForUpdate(7L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);

        Map<String, Object> stats = recoveryService.applyRecovery(player);

        assertTrue(((Number) stats.get("exp")).intValue() >= 102);
        assertEquals(2, ((Number) stats.get("level")).intValue());
        assertEquals(7, ((Number) stats.get("freePoints")).intValue());
        assertEquals(100, ((Number) stats.get("hp")).intValue());
        assertEquals(400, ((Number) stats.get("maxExp")).intValue());
        verify(playerMapper).updateById(player);
        verify(playerLogService).addLog(7L, "milestone", "你的修为提升至等级 2，获得 3 点自由属性点。");
    }

    @Test
    void passiveExperienceCanApplySeveralMissedLevelsAtOnce() {
        when(resourceTransactionService.record(any(PlayerResourceTransactionService.Change.class))).thenReturn(true);
        Player player = new Player();
        player.setId(8L);
        long lastRecoveryAt = System.currentTimeMillis() - 12 * 60 * 60 * 1_000L;
        player.setStatsJson("{\"hp\":80,\"maxHp\":100,\"stamina\":50,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"freePoints\":2,\"isResting\":true,"
                + "\"lastRecoveryAt\":" + lastRecoveryAt + "}");
        when(playerMapper.selectByIdForUpdate(8L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);

        Map<String, Object> stats = recoveryService.applyRecovery(player);

        assertEquals(4, ((Number) stats.get("level")).intValue());
        assertEquals(11, ((Number) stats.get("freePoints")).intValue());
        assertEquals(1_600, ((Number) stats.get("maxExp")).intValue());
        verify(playerLogService).addLog(8L, "milestone", "你的修为提升至等级 4，获得 9 点自由属性点。");
    }

    @Test
    void previouslyAccumulatedExperienceIsReconciledWithoutWaitingForAnotherTick() {
        Player player = new Player();
        player.setId(9L);
        long lastRecoveryAt = System.currentTimeMillis() - 10_000L;
        player.setStatsJson("{\"hp\":80,\"maxHp\":100,\"stamina\":50,\"maxStamina\":50,"
                + "\"exp\":101,\"level\":1,\"freePoints\":4,\"isResting\":true,"
                + "\"lastRecoveryAt\":" + lastRecoveryAt + "}");
        when(playerMapper.selectByIdForUpdate(9L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);

        Map<String, Object> stats = recoveryService.applyRecovery(player);

        assertEquals(101, ((Number) stats.get("exp")).intValue());
        assertEquals(2, ((Number) stats.get("level")).intValue());
        assertEquals(7, ((Number) stats.get("freePoints")).intValue());
        verify(playerLogService).addLog(9L, "milestone", "你的修为提升至等级 2，获得 3 点自由属性点。");
    }

    @Test
    void passiveRecoveryDoesNotReportProgressWhenPlayerWriteFails() {
        Player player = new Player();
        player.setId(10L);
        long lastRecoveryAt = System.currentTimeMillis() - 90_000L;
        player.setStatsJson("{\"hp\":80,\"maxHp\":100,\"stamina\":25,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"isResting\":true,\"lastRecoveryAt\":" + lastRecoveryAt + "}");
        when(playerMapper.selectByIdForUpdate(10L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> recoveryService.applyRecovery(player));

        verifyNoInteractions(playerLogService);
    }

    @Test
    void startRestDoesNotLogSuccessWhenPlayerWriteFails() {
        Player player = new Player();
        player.setId(11L);
        player.setUserId(9L);
        player.setStatsJson("{\"hp\":80,\"maxHp\":100,\"stamina\":25,\"isResting\":false}");
        when(playerMapper.selectByIdForUpdate(11L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> recoveryService.startRest(11L, 9L));

        verifyNoInteractions(playerLogService);
    }

    @Test
    void stopRestDoesNotLogSuccessWhenPlayerWriteFails() {
        Player player = new Player();
        player.setId(12L);
        player.setUserId(9L);
        player.setStatsJson("{\"hp\":80,\"maxHp\":100,\"stamina\":25,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"isResting\":true,\"lastRecoveryAt\":9223372036854775807}");
        when(playerMapper.selectByIdForUpdate(12L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> recoveryService.stopRest(12L, 9L));

        verifyNoInteractions(playerLogService);
        verify(playerMapper).updateById(player);
    }

    @Test
    void recoveryUsesTheLatestLockedPlayerStatsInsteadOfAStaleCallerSnapshot() {
        Player stalePlayer = new Player();
        stalePlayer.setId(13L);
        stalePlayer.setStatsJson("{\"hp\":10,\"maxHp\":100}");
        Player currentPlayer = new Player();
        currentPlayer.setId(13L);
        currentPlayer.setStatsJson("{\"hp\":80,\"maxHp\":100,\"lastRecoveryAt\":9223372036854775807}");
        when(playerMapper.selectByIdForUpdate(13L)).thenReturn(currentPlayer);

        Map<String, Object> stats = recoveryService.applyRecovery(stalePlayer);

        assertEquals(80, ((Number) stats.get("hp")).intValue());
        assertEquals(currentPlayer.getStatsJson(), stalePlayer.getStatsJson());
        verify(playerMapper, never()).updateById(currentPlayer);
    }

    @Test
    void equippedStaminaCapacityBonusAppliesToRecoveryWithoutPersistingAsBaseGrowth() throws Exception {
        when(resourceTransactionService.record(any(PlayerResourceTransactionService.Change.class))).thenReturn(true);
        when(staminaProjectionService.projectMaxStamina(70L, 50)).thenReturn(55);
        Player player = new Player();
        player.setId(70L);
        long lastRecoveryAt = System.currentTimeMillis() - 200_000L;
        player.setStatsJson("{\"hp\":100,\"maxHp\":100,\"stamina\":54,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"isResting\":true,\"lastRecoveryAt\":"
                + lastRecoveryAt + "}");
        when(playerMapper.selectByIdForUpdate(70L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);

        Map<String, Object> stats = recoveryService.applyRecovery(player);

        assertEquals(55, ((Number) stats.get("stamina")).intValue());
        assertEquals(50, ((Number) stats.get("maxStamina")).intValue());
        assertEquals(196, ((Number) stats.get("staminaIntervalSeconds")).intValue());
    }

    @Test
    void recoveryPreservesRankAndPrestigeHpAndReplacesPreviouslyAppliedConstellationBonus() {
        AvatarRankConfig eRank = new AvatarRankConfig();
        eRank.setRankKey("E");
        eRank.setOrderNum(2);
        eRank.setRewardsJson("{\"stats\":{\"maxHp\":20}}");
        when(rankConfigMapper.selectList(any())).thenReturn(List.of(eRank));

        Player player = new Player();
        player.setId(14L);
        long lastRecoveryAt = System.currentTimeMillis() - 5_000L;
        player.setStatsJson("{\"hp\":80,\"maxHp\":120,\"stamina\":50,\"maxStamina\":50,"
                + "\"avatarRank\":\"E\",\"prestigeLevel\":2,\"constellation\":\"master_of_steel\","
                + "\"isResting\":true,\"lastRecoveryAt\":" + lastRecoveryAt + "}");
        when(playerMapper.selectByIdForUpdate(14L)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);

        Map<String, Object> firstRecovery = recoveryService.applyRecovery(player);
        Map<String, Object> secondRecovery = recoveryService.applyRecovery(player);

        assertEquals(150, ((Number) firstRecovery.get("maxHp")).intValue());
        assertEquals(20, ((Number) firstRecovery.get("maxHpConstellationBonus")).intValue());
        assertEquals(150, ((Number) secondRecovery.get("maxHp")).intValue());
        verify(playerMapper).updateById(player);
    }
}
