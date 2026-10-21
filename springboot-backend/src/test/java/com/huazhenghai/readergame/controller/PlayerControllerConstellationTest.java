package com.huazhenghai.readergame.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.ConstellationFactionMapper;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.PkService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerStaminaProjectionService;
import com.huazhenghai.readergame.service.PlayerService;
import com.huazhenghai.readergame.service.RecoveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerControllerConstellationTest {
    private static final Long PLAYER_ID = 42L;
    private static final Long USER_ID = 7L;

    @Mock private PlayerService playerService;
    @Mock private RecoveryService recoveryService;
    @Mock private AvatarRankConfigMapper rankConfigMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerFactionMapper playerFactionMapper;
    @Mock private ConstellationFactionMapper constellationFactionMapper;
    @Mock private LocationMapper locationMapper;
    @Mock private PkService pkService;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private PlayerStaminaProjectionService staminaProjectionService;

    private PlayerController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PlayerMaxHpProjectionService maxHpProjectionService =
                new PlayerMaxHpProjectionService(rankConfigMapper, objectMapper);
        controller = new PlayerController(playerService, recoveryService, maxHpProjectionService, playerMapper,
                playerFactionMapper, constellationFactionMapper, locationMapper,
                objectMapper, pkService, playerLogService,
                actionReceiptService, resourceTransactionService, staminaProjectionService);
        LoginUserContext.set(new LoginUser(USER_ID, "test-user", "player"));
    }

    @AfterEach
    void clearLoginUser() {
        LoginUserContext.clear();
    }

    @Test
    void playerAndRestEndpointsDisplayEffectiveStaminaCapacity() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        Map<String, Object> playerStats = new HashMap<>(Map.of("stamina", 50, "maxStamina", 50));
        var playerVO = com.huazhenghai.readergame.vo.PlayerVO.builder()
                .id(PLAYER_ID).stats(playerStats).build();
        when(playerService.findByUserId(USER_ID)).thenReturn(player);
        when(playerService.getPlayer(PLAYER_ID)).thenReturn(playerVO);
        when(staminaProjectionService.projectMaxStamina(PLAYER_ID, 50)).thenReturn(55);

        var playerResult = controller.getMyPlayer();

        assertEquals(55, ((Number) playerResult.getData().getStats().get("maxStamina")).intValue());

        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(recoveryService.applyRecovery(player)).thenReturn(Map.of(
                "isResting", false, "hp", 100, "maxHp", 100,
                "stamina", 50, "maxStamina", 50, "level", 1,
                "hpIntervalSeconds", 1, "staminaIntervalSeconds", 1, "expIntervalSeconds", 30));

        var restResult = controller.getRestState(PLAYER_ID);

        assertEquals(55, restResult.getData().getMaxStamina());
    }

    @Test
    void changeConstellationLocksPlayerAndLogsExactFragmentSpend() throws Exception {
        Player player = playerWithFragments(250);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(resourceTransactionService.record(any(
                PlayerResourceTransactionService.Change.class))).thenReturn(true);

        var response = controller.changeConstellation(Map.of(
                "playerId", PLAYER_ID,
                "constellationKey", "morning_star"));

        assertTrue(response.isSuccess());
        assertEquals(50, player.getStoryFragments());
        Map<?, ?> updatedStats = new ObjectMapper().readValue(player.getStatsJson(), Map.class);
        assertEquals("morning_star", updatedStats.get("constellation"));
        assertEquals(50, updatedStats.get("constellationFavor"));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> controller.changeConstellation(Map.of(
                        "playerId", PLAYER_ID,
                        "constellationKey", "morning_star")));
        assertEquals("新背后星与当前相同", duplicate.getMessage());
        assertEquals(50, player.getStoryFragments());
        verify(playerMapper, times(2)).selectByIdForUpdate(PLAYER_ID);
        verify(playerMapper).updateById(player);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(playerLogService).addLog(eq(PLAYER_ID), eq("constellation_change"),
                message.capture(), metadata.capture());
        assertTrue(message.getValue().contains("消耗 200 故事碎片"));
        assertEquals("storyFragments", metadata.getValue().get("resource"));
        assertEquals(200, metadata.getValue().get("cost"));
        assertEquals("demon_judge_of_fire", metadata.getValue().get("fromConstellation"));
        assertEquals("morning_star", metadata.getValue().get("toConstellation"));
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChange =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(resourceChange.capture());
        assertEquals("currency", resourceChange.getValue().resourceType());
        assertEquals("story_fragments", resourceChange.getValue().resourceKey());
        assertEquals(-200L, resourceChange.getValue().amount());
        assertEquals(250L, resourceChange.getValue().balanceBefore());
        assertEquals(50L, resourceChange.getValue().balanceAfter());
        verify(playerLogService, times(1)).addLog(eq(PLAYER_ID), eq("constellation_change"),
                any(String.class), anyMap());
        assertNotNull(PlayerController.class
                .getMethod("changeConstellation", Map.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void changeConstellationDoesNotSpendWhenStoryFragmentsAreInsufficient() {
        Player player = playerWithFragments(199);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.changeConstellation(Map.of(
                        "playerId", PLAYER_ID,
                        "constellationKey", "morning_star")));

        assertEquals("故事碎片不足，更换背后星需要 200 故事碎片（当前: 199）", error.getMessage());
        assertEquals(199, player.getStoryFragments());
        verify(playerMapper).selectByIdForUpdate(PLAYER_ID);
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void changeConstellationFailsBeforeLoggingWhenResourceWriteFails() {
        Player player = playerWithFragments(250);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> controller.changeConstellation(Map.of(
                        "playerId", PLAYER_ID,
                        "constellationKey", "morning_star")));

        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
    }

    @Test
    void changeConstellationFailsWhenResourceLedgerCannotBeWritten() {
        Player player = playerWithFragments(250);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(resourceTransactionService.record(any(
                PlayerResourceTransactionService.Change.class))).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> controller.changeConstellation(Map.of(
                        "playerId", PLAYER_ID,
                        "constellationKey", "morning_star")));

        verify(playerLogService, never()).addLog(any(), any(), any(), anyMap());
        verify(resourceTransactionService).record(any(PlayerResourceTransactionService.Change.class));
    }

    private Player playerWithFragments(int fragments) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        player.setStoryFragments(fragments);
        player.setStatsJson("{\"constellation\":\"demon_judge_of_fire\",\"constellationFavor\":100}");
        return player;
    }
}
