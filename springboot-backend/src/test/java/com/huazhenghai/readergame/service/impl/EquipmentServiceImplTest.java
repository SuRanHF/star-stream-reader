package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Equipment;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerEquipment;
import com.huazhenghai.readergame.mapper.EquipmentMapper;
import com.huazhenghai.readergame.mapper.EquipmentSetMapper;
import com.huazhenghai.readergame.mapper.PlayerEquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.EquipmentGrantResult;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentSetMapper equipmentSetMapper;
    @Mock private PlayerEquipmentMapper playerEquipmentMapper;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private PlayerActionReceiptService actionReceiptService;

    private EquipmentServiceImpl equipmentService;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentServiceImpl(
                playerMapper, equipmentMapper, equipmentSetMapper, playerEquipmentMapper,
                playerLogService, resourceTransactionService, actionReceiptService, new ObjectMapper());
    }

    @Test
    void specialEffectBonusesAggregateOnlyFiniteNumericEffectsFromEquipmentDefinitions() {
        PlayerEquipment firstEquipped = new PlayerEquipment();
        firstEquipped.setPlayerId(1L);
        firstEquipped.setEquipmentKey("reader_scrap_blade");
        firstEquipped.setEquipped(1);
        firstEquipped.setDurability(100);
        PlayerEquipment secondEquipped = new PlayerEquipment();
        secondEquipped.setPlayerId(1L);
        secondEquipped.setEquipmentKey("starfall_blade");
        secondEquipped.setEquipped(1);
        secondEquipped.setDurability(100);
        when(playerEquipmentMapper.selectList(any()))
                .thenReturn(java.util.List.of(firstEquipped, secondEquipped));

        Equipment firstDefinition = new Equipment();
        firstDefinition.setEnabled(1);
        firstDefinition.setSpecialEffectsJson("{\"storyEventBonus\":0.02,\"abyssVision\":true}");
        Equipment secondDefinition = new Equipment();
        secondDefinition.setEnabled(1);
        secondDefinition.setSpecialEffectsJson("{\"storyEventBonus\":0.05,\"label\":\"ignored\"}");
        when(equipmentMapper.selectOne(any())).thenReturn(firstDefinition, secondDefinition);

        Map<String, Object> bonuses = equipmentService.calculateEquipmentSpecialEffectBonuses(1L);

        assertEquals(0.07, (double) bonuses.get("storyEventBonus"), 1.0e-9);
        org.junit.jupiter.api.Assertions.assertFalse(bonuses.containsKey("abyssVision"));
        org.junit.jupiter.api.Assertions.assertFalse(bonuses.containsKey("label"));
    }

    @Test
    void repairLocksPlayerBeforeChargingCoinsAndRestoringDurability() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(100);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setPlayerId(1L);
        owned.setEquipmentKey("station_blade");
        owned.setDurability(60);
        owned.setListed(0);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned);

        Equipment definition = new Equipment();
        definition.setEquipmentKey("station_blade");
        definition.setName("车站短刃");
        definition.setEnabled(1);
        definition.setMaxDurability(100);
        definition.setRepairCost(15);
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerEquipmentMapper.updateById(owned)).thenReturn(1);

        Map<String, Object> result = equipmentService.repair(1L, "station_blade", 9L);

        assertEquals(85, player.getCoins());
        assertEquals(100, owned.getDurability());
        assertEquals(85, result.get("remainingCoins"));
        assertEquals(15, result.get("cost"));
        assertEquals(15, result.get("repairCost"));
        verify(playerMapper).selectByIdForUpdate(1L);
        verify(playerMapper).updateById(player);
        verify(playerEquipmentMapper).updateById(owned);
        ArgumentCaptor<PlayerResourceTransactionService.Change> ledgerChange =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(ledgerChange.capture());
        assertEquals("currency", ledgerChange.getValue().resourceType());
        assertEquals("coins", ledgerChange.getValue().resourceKey());
        assertEquals(-15L, ledgerChange.getValue().amount());
        assertEquals(100L, ledgerChange.getValue().balanceBefore());
        assertEquals(85L, ledgerChange.getValue().balanceAfter());
        assertEquals("equipment_repair", ledgerChange.getValue().sourceType());
        assertEquals("station_blade", ledgerChange.getValue().sourceId());
    }

    @Test
    void repairDoesNotContinueWhenTheCoinDeductionWriteFails() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(100);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setEquipmentKey("station_blade");
        owned.setDurability(60);
        owned.setListed(0);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned);

        Equipment definition = new Equipment();
        definition.setEquipmentKey("station_blade");
        definition.setName("车站短刃");
        definition.setEnabled(1);
        definition.setMaxDurability(100);
        definition.setRepairCost(15);
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(playerMapper.updateById(player)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> equipmentService.repair(1L, "station_blade", 9L));

        verify(playerEquipmentMapper, never()).updateById(any(PlayerEquipment.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void repairDoesNotReportSuccessWhenTheDurabilityWriteFails() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(100);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setEquipmentKey("station_blade");
        owned.setDurability(60);
        owned.setListed(0);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned);

        Equipment definition = new Equipment();
        definition.setEquipmentKey("station_blade");
        definition.setName("车站短刃");
        definition.setEnabled(1);
        definition.setMaxDurability(100);
        definition.setRepairCost(15);
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerEquipmentMapper.updateById(owned)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> equipmentService.repair(1L, "station_blade", 9L));

        verify(playerLogService, never()).addLog(any(), any(), any(), any());
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void equipDoesNotReportSuccessWhenTheEquipmentStateWriteFails() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setEquipmentKey("hunter_blade");
        owned.setDurability(60);
        owned.setEquipped(0);
        owned.setListed(0);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned).thenReturn(null);
        when(playerEquipmentMapper.updateById(owned)).thenReturn(0);
        when(equipmentMapper.selectOne(any())).thenReturn(equipmentDefinition());

        assertThrows(IllegalStateException.class,
                () -> equipmentService.equip(1L, "hunter_blade", 9L));

        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void unequipDoesNotReportSuccessWhenTheEquipmentStateWriteFails() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setEquipmentKey("hunter_blade");
        owned.setEquipped(1);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned);
        when(playerEquipmentMapper.updateById(owned)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> equipmentService.unequip(1L, "hunter_blade", 9L));

        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void addEquipmentReportsSuccessOnlyAfterTheOwnershipRowIsInserted() {
        Equipment definition = equipmentDefinition();
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(null);
        when(playerEquipmentMapper.insert(any(PlayerEquipment.class))).thenReturn(0);

        int granted = equipmentService.addEquipment(1L, "hunter_blade", "synthesis");

        assertEquals(0, granted);
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void addEquipmentCreatesAnOwnedEquipmentRowWithFreshDurability() {
        Equipment definition = equipmentDefinition();
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(null);
        when(playerEquipmentMapper.insert(any(PlayerEquipment.class))).thenReturn(1);

        EquipmentGrantResult grant = equipmentService.addEquipmentWithResult(
                1L, "hunter_blade", "synthesis");

        assertEquals(1, grant.quantityGranted());
        assertEquals("猎人短刃", grant.equipmentName());
        ArgumentCaptor<PlayerEquipment> inserted = ArgumentCaptor.forClass(PlayerEquipment.class);
        verify(playerEquipmentMapper).insert(inserted.capture());
        assertEquals(1L, inserted.getValue().getPlayerId());
        assertEquals("hunter_blade", inserted.getValue().getEquipmentKey());
        assertEquals(60, inserted.getValue().getDurability());
        verify(playerLogService).addLog(1L, "equipment_acquire", "获得装备: 猎人短刃",
                java.util.Map.of("equipmentKey", "hunter_blade", "source", "synthesis"));
    }

    @Test
    void enhancementConsumesCoinsRaisesLevelAndScalesCombatStats() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(500);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setPlayerId(1L);
        owned.setEquipmentKey("hunter_blade");
        owned.setEnhancementLevel(2);
        owned.setDurability(60);
        owned.setEquipped(1);
        owned.setListed(0);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned);
        when(playerEquipmentMapper.updateById(owned)).thenReturn(1);

        Equipment definition = equipmentDefinition();
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(playerMapper.updateById(player)).thenReturn(1);

        Map<String, Object> result = equipmentService.enhance(1L, "hunter_blade", 9L, "enhance-request-1");

        assertEquals(3, owned.getEnhancementLevel());
        assertEquals(320, player.getCoins());
        assertEquals(180, result.get("cost"));
        assertEquals(3, result.get("enhancementLevel"));
        Map<?, ?> enhancedStats = (Map<?, ?>) result.get("stats");
        assertEquals(23.0, ((Number) enhancedStats.get("attack")).doubleValue(), 0.0001);
        assertEquals(0.115, ((Number) enhancedStats.get("critRate")).doubleValue(), 0.0001);
        verify(playerMapper).updateById(player);
        verify(playerEquipmentMapper).updateById(owned);
        verify(playerLogService).addLog(1L, "equipment_enhance",
                "强化了 猎人短刃 至 +3（基础属性 +15%，花费 180 星币）",
                Map.of("equipmentKey", "hunter_blade", "fromLevel", 2,
                        "toLevel", 3, "cost", 180, "bonusPercent", 15));
        ArgumentCaptor<PlayerResourceTransactionService.Change> ledgerChange =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(ledgerChange.capture());
        assertEquals("coins", ledgerChange.getValue().resourceKey());
        assertEquals(-180L, ledgerChange.getValue().amount());
        assertEquals(500L, ledgerChange.getValue().balanceBefore());
        assertEquals(320L, ledgerChange.getValue().balanceAfter());
        assertEquals("equipment_enhance", ledgerChange.getValue().sourceType());
        verify(actionReceiptService).record(1L, "equipment.enhance", "enhance-request-1",
                Map.of("playerId", 1L, "equipmentKey", "hunter_blade"), result);
    }

    @Test
    void combatEquipmentBonusIncludesEnhancementWithoutCompoundingStoredBaseStats() {
        PlayerEquipment owned = new PlayerEquipment();
        owned.setPlayerId(1L);
        owned.setEquipmentKey("hunter_blade");
        owned.setEquipped(1);
        owned.setDurability(60);
        owned.setEnhancementLevel(2);
        when(playerEquipmentMapper.selectList(any())).thenReturn(java.util.List.of(owned));
        when(equipmentMapper.selectOne(any())).thenReturn(equipmentDefinition());

        Map<String, Object> first = equipmentService.calculateEquipmentBonus(1L);
        Map<String, Object> second = equipmentService.calculateEquipmentBonus(1L);

        assertEquals(22.0, ((Number) first.get("attack")).doubleValue(), 0.0001);
        assertEquals(0.11, ((Number) first.get("critRate")).doubleValue(), 0.0001);
        assertEquals(first, second);
    }

    @Test
    void enhancementRefusesInsufficientCoinsWithoutChangingEitherRow() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(100);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment owned = new PlayerEquipment();
        owned.setPlayerId(1L);
        owned.setEquipmentKey("hunter_blade");
        owned.setEnhancementLevel(2);
        owned.setListed(0);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(owned);
        when(equipmentMapper.selectOne(any())).thenReturn(equipmentDefinition());

        BusinessException error = assertThrows(BusinessException.class,
                () -> equipmentService.enhance(1L, "hunter_blade", 9L, "enhance-insufficient"));

        assertEquals("星币不足，强化需要 180 星币", error.getMessage());
        assertEquals(100, player.getCoins());
        assertEquals(2, owned.getEnhancementLevel());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerEquipmentMapper, never()).updateById(any(PlayerEquipment.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void listedEquipmentAndEquipmentAtTheCapCannotBeEnhanced() {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(10_000);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);

        PlayerEquipment listed = new PlayerEquipment();
        listed.setEquipmentKey("hunter_blade");
        listed.setListed(1);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(listed, listed);
        when(equipmentMapper.selectOne(any())).thenReturn(equipmentDefinition());
        BusinessException listedError = assertThrows(BusinessException.class,
                () -> equipmentService.enhance(1L, "hunter_blade", 9L, "enhance-listed"));
        assertEquals("已上架交易的装备不能强化，请先取消挂单", listedError.getMessage());

        listed.setListed(0);
        listed.setEnhancementLevel(10);
        BusinessException cappedError = assertThrows(BusinessException.class,
                () -> equipmentService.enhance(1L, "hunter_blade", 9L, "enhance-capped"));
        assertEquals("装备已达到强化上限 +10", cappedError.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerEquipmentMapper, never()).updateById(any(PlayerEquipment.class));
    }

    private static Equipment equipmentDefinition() {
        Equipment definition = new Equipment();
        definition.setEquipmentKey("hunter_blade");
        definition.setName("猎人短刃");
        definition.setSlot("weapon");
        definition.setEnabled(1);
        definition.setMaxDurability(60);
        definition.setRepairCost(20);
        definition.setSellPrice(100);
        definition.setBaseStatsJson("{\"attack\":20,\"critRate\":0.1}");
        return definition;
    }
}
