package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.dto.SellItemCommand;
import com.huazhenghai.readergame.entity.Equipment;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerEquipment;
import com.huazhenghai.readergame.entity.PlayerInventory;
import com.huazhenghai.readergame.entity.SynthesisRecipe;
import com.huazhenghai.readergame.mapper.EquipmentMapper;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.PlayerEquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.SynthesisRecipeMapper;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerStaminaProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private PlayerInventoryMapper inventoryMapper;
    @Mock private SynthesisRecipeMapper recipeMapper;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerEquipmentMapper playerEquipmentMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentService equipmentService;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerStaminaProjectionService staminaProjectionService;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(playerMapper, itemMapper, inventoryMapper,
                recipeMapper, playerLogService, new ObjectMapper(), playerEquipmentMapper, equipmentMapper,
                equipmentService, playerEffectService, resourceTransactionService, actionReceiptService,
                staminaProjectionService);
    }

    @Test
    void grantsOnlyTheRemainingStackCapacity() {
        Item item = item(10);
        PlayerInventory existing = inventory("herb", 9);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(existing);
        when(inventoryMapper.updateById(existing)).thenReturn(1);

        ItemGrantResult result = inventoryService.addItemWithResult(1L, "herb", 5);

        assertEquals(1, result.quantityGranted());
        assertEquals(10, result.totalQuantity());
        assertEquals("草药", result.itemName());
        assertEquals(10, existing.getQuantity());
        verify(inventoryMapper).updateById(existing);
    }

    @Test
    void fullStackReturnsZeroWithoutUpdatingInventory() {
        when(itemMapper.selectOne(any())).thenReturn(item(10));
        when(inventoryMapper.selectOne(any())).thenReturn(inventory("herb", 10));

        ItemGrantResult result = inventoryService.addItemWithResult(1L, "herb", 3);

        assertEquals(0, result.quantityGranted());
        assertEquals(10, result.totalQuantity());
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).insert(any(PlayerInventory.class));
    }

    @Test
    void newStackIsCappedAndReturnsActualQuantityGranted() {
        when(itemMapper.selectOne(any())).thenReturn(item(3));
        when(inventoryMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.insert(any(PlayerInventory.class))).thenReturn(1);

        ItemGrantResult result = inventoryService.addItemWithResult(1L, "herb", 5);

        assertEquals(3, result.quantityGranted());
        assertEquals(3, result.totalQuantity());
        ArgumentCaptor<PlayerInventory> inserted = ArgumentCaptor.forClass(PlayerInventory.class);
        verify(inventoryMapper).insert(inserted.capture());
        assertEquals(3, inserted.getValue().getQuantity());
    }

    @Test
    void failedDatabaseUpdateIsNotReportedAsAnItemGrant() {
        PlayerInventory existing = inventory("herb", 4);
        when(itemMapper.selectOne(any())).thenReturn(item(10));
        when(inventoryMapper.selectOne(any())).thenReturn(existing);
        when(inventoryMapper.updateById(existing)).thenReturn(0);

        ItemGrantResult result = inventoryService.addItemWithResult(1L, "herb", 3);

        assertEquals(0, result.quantityGranted());
        assertEquals(4, result.totalQuantity());
    }

    @Test
    void failedDatabaseInsertIsNotReportedAsAnItemGrant() {
        when(itemMapper.selectOne(any())).thenReturn(item(10));
        when(inventoryMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.insert(any(PlayerInventory.class))).thenReturn(0);

        ItemGrantResult result = inventoryService.addItemWithResult(1L, "herb", 3);

        assertEquals(0, result.quantityGranted());
        assertEquals(0, result.totalQuantity());
    }

    @Test
    void unavailableItemDoesNotReportAnyGrantOrTouchInventory() {
        when(itemMapper.selectOne(any())).thenReturn(null);

        ItemGrantResult result = inventoryService.addItemWithResult(1L, "removed_item", 2);

        assertEquals(0, result.quantityGranted());
        assertEquals(0, result.totalQuantity());
        verify(inventoryMapper, never()).selectOne(any());
        verify(inventoryMapper, never()).insert(any(PlayerInventory.class));
    }

    @Test
    void legacyAddItemReturnValueRemainsTheNewStackTotal() {
        Item item = item(10);
        PlayerInventory existing = inventory("herb", 4);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(existing);
        when(inventoryMapper.updateById(existing)).thenReturn(1);

        int totalQuantity = inventoryService.addItem(1L, "herb", 3);

        assertEquals(7, totalQuantity);
    }

    @Test
    void useItemLocksTheInventoryRowBeforeConsumingIt() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{}");
        Item potion = item("potion", "治疗药剂", 10);
        potion.setConsumeOnUse(1);
        potion.setEffectsJson("{\"heal_hp\":1}");
        PlayerInventory inventory = inventory("potion", 2);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(potion);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.updateById(inventory)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);

        var result = inventoryService.useItem(1L, "potion", 9L, "use-potion-1");

        assertEquals(1, result.getRemainingQuantity());
        assertEquals(1, inventory.getQuantity());
        ArgumentCaptor<Wrapper<PlayerInventory>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(inventoryMapper).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        var lockOrder = inOrder(playerMapper, inventoryMapper);
        lockOrder.verify(playerMapper).selectByIdForUpdate(1L);
        lockOrder.verify(inventoryMapper).selectOne(any());
    }

    @Test
    void useItemAppliesAndPersistsEffectsBeforeReportingSuccess() throws Exception {
        Player player = playerWithCoins(0);
        player.setStoryFragments(0);
        player.setStatsJson("{\"stamina\":20,\"maxStamina\":100}");
        Item item = item("tonic", "星流补剂", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"restore_stamina\":30,\"story_fragments\":10,\"channel_heat\":5}");
        PlayerInventory inventory = inventory("tonic", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.deleteById(inventory)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerEffectService.applyChannelHeatGainRate(1L, 5)).thenReturn(5);
        when(staminaProjectionService.projectMaxStamina(1L, 100)).thenReturn(100);

        var result = inventoryService.useItem(1L, "tonic", 9L, "use-tonic-1");

        assertEquals(10, player.getStoryFragments());
        assertEquals(30, result.getAppliedEffects().get("stamina"));
        assertEquals(10, result.getAppliedEffects().get("storyFragments"));
        assertEquals(5, result.getAppliedEffects().get("channelHeat"));
        assertEquals(50, new ObjectMapper().readTree(player.getStatsJson()).path("stamina").asInt());
        assertEquals(0, result.getRemainingQuantity());
        ArgumentCaptor<java.util.Map> metadata = ArgumentCaptor.forClass(java.util.Map.class);
        verify(playerLogService).addLog(eq(1L), eq("item_use"), eq("使用了 星流补剂"), metadata.capture());
        assertEquals("item_use", metadata.getValue().get("kind"));
        assertEquals("tonic", metadata.getValue().get("itemKey"));
        assertEquals("星流补剂", metadata.getValue().get("itemName"));
        assertEquals(java.util.Map.of("stamina", 30, "storyFragments", 10, "channelHeat", 5),
                metadata.getValue().get("appliedEffects"));
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChanges =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        org.mockito.Mockito.verify(resourceTransactionService, org.mockito.Mockito.times(4))
                .record(resourceChanges.capture());
        var byResource = resourceChanges.getAllValues().stream().collect(
                java.util.stream.Collectors.toMap(
                        change -> change.resourceType() + ":" + change.resourceKey(), change -> change));
        assertEquals(-1L, byResource.get("item:tonic").amount());
        assertEquals(1L, byResource.get("item:tonic").balanceBefore());
        assertEquals(0L, byResource.get("item:tonic").balanceAfter());
        assertEquals(10L, byResource.get("currency:story_fragments").amount());
        assertEquals(30L, byResource.get("energy:stamina").amount());
        assertEquals(5L, byResource.get("progress:channel_heat").amount());
    }

    @Test
    void staminaRestorationUsesProjectedCapacityWithoutPersistingItAsBaseCapacity() throws Exception {
        Player player = playerWithCoins(0);
        player.setStatsJson("{\"stamina\":50,\"maxStamina\":50}");
        Item item = item("tonic", "星流补剂", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"restore_stamina\":10}");
        PlayerInventory inventory = inventory("tonic", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.deleteById(inventory)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(staminaProjectionService.projectMaxStamina(1L, 50)).thenReturn(55);

        var result = inventoryService.useItem(1L, "tonic", 9L, "use-tonic-projected-capacity");

        assertEquals(5, result.getAppliedEffects().get("stamina"));
        var savedStats = new ObjectMapper().readTree(player.getStatsJson());
        assertEquals(55, savedStats.path("stamina").asInt());
        assertEquals(50, savedStats.path("maxStamina").asInt());
    }

    @Test
    void staminaPotionIsNotConsumedWhenCurrentStaminaAlreadyExceedsEffectiveCapacity() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{\"stamina\":55,\"maxStamina\":50}");
        Item item = item("tonic", "星流补剂", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"restore_stamina\":10}");
        PlayerInventory inventory = inventory("tonic", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(staminaProjectionService.projectMaxStamina(1L, 50)).thenReturn(50);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "tonic", 9L, "use-tonic-over-cap"));

        assertEquals("当前状态无法获得该物品效果，物品未消耗", error.getMessage());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void useItemRejectsStoryFragmentOverflowWithoutConsumingTheItem() {
        Player player = playerWithCoins(0);
        player.setStoryFragments(Integer.MAX_VALUE);
        Item item = item("fragment_core", "故事核心", 1);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"story_fragments\":1}");
        PlayerInventory inventory = inventory("fragment_core", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "fragment_core", 9L, "use-fragment-overflow"));

        assertEquals("故事碎片已达到可保存上限，暂时无法领取", error.getMessage());
        assertEquals(Integer.MAX_VALUE, player.getStoryFragments());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any());
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void useItemRejectsChannelHeatOverflowWithoutConsumingTheItem() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{\"channelHeat\":" + Integer.MAX_VALUE + "}");
        Item item = item("heat_tonic", "热度药剂", 1);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"channel_heat\":1}");
        PlayerInventory inventory = inventory("heat_tonic", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(playerEffectService.applyChannelHeatGainRate(1L, 1)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "heat_tonic", 9L, "use-heat-overflow"));

        assertEquals("星流热度超出可保存范围，操作无法完成", error.getMessage());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any());
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void useItemRejectsWorldLineShiftUnderflowWithoutConsumingTheItem() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{\"worldLineShift\":" + Integer.MIN_VALUE + "}");
        Item item = item("shift_tonic", "偏移药剂", 1);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"world_line_shift\":-1}");
        PlayerInventory inventory = inventory("shift_tonic", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "shift_tonic", 9L, "use-shift-overflow"));

        assertEquals("世界线偏移超出可保存范围，操作无法完成", error.getMessage());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any());
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void useItemDoesNotSaveOrLogEffectsWhenInventoryConsumptionFails() {
        Player player = playerWithCoins(0);
        player.setStoryFragments(0);
        player.setStatsJson("{}");
        Item item = item("core", "故事碎片核心", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"story_fragments\":15}");
        PlayerInventory inventory = inventory("core", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.deleteById(inventory)).thenReturn(0);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> inventoryService.useItem(1L, "core", 9L, "use-core-1"));

        assertEquals("消耗物品失败: core", error.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void useItemDoesNotReportSuccessWhenPlayerEffectPersistenceFails() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{}");
        Item item = item("bandage", "急救绷带", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"heal_hp\":50}");
        PlayerInventory inventory = inventory("bandage", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.deleteById(inventory)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(0);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> inventoryService.useItem(1L, "bandage", 9L, "use-bandage-1"));

        assertEquals("保存物品效果失败: bandage", error.getMessage());
        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void useItemDoesNotConsumeItemWhenEffectsCannotBeSerialized() {
        inventoryService = new InventoryServiceImpl(playerMapper, itemMapper, inventoryMapper,
                recipeMapper, playerLogService, new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("模拟写入失败") {};
                    }
                }, playerEquipmentMapper, equipmentMapper, equipmentService, playerEffectService,
                resourceTransactionService, actionReceiptService, staminaProjectionService);
        Player player = playerWithCoins(0);
        player.setStatsJson("{}");
        Item item = item("bandage", "急救绷带", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"heal_hp\":50}");
        PlayerInventory inventory = inventory("bandage", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.deleteById(inventory)).thenReturn(1);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> inventoryService.useItem(1L, "bandage", 9L, "use-bandage-2"));

        assertEquals("物品效果序列化失败: bandage", error.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void useItemDoesNotConsumeAHealingItemWhenHealthIsAlreadyFull() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{\"hp\":100,\"maxHp\":100}");
        Item item = item("bandage", "急救绷带", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"heal_hp\":50}");
        PlayerInventory inventory = inventory("bandage", 1);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "bandage", 9L, "use-bandage-3"));

        assertEquals("当前状态无法获得该物品效果，物品未消耗", error.getMessage());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void useItemRejectsUnknownEffectsWithoutConsumingTheItem() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{}");
        Item item = item("mystery", "未知药剂", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{\"restore_mana\":20}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory("mystery", 1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "mystery", 9L, "use-mystery-1"));

        assertEquals("物品效果暂不支持，未消耗: restore_mana", error.getMessage());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any());
    }

    @Test
    void useItemRejectsAnEmptyEffectConfigurationWithoutConsumingTheItem() {
        Player player = playerWithCoins(0);
        player.setStatsJson("{}");
        Item item = item("empty", "空效药剂", 10);
        item.setConsumeOnUse(1);
        item.setEffectsJson("{}");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory("empty", 1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.useItem(1L, "empty", 9L, "use-empty-1"));

        assertEquals("物品没有配置可用效果，未消耗: empty", error.getMessage());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void sellItemLocksPlayerAndInventoryBeforePayingOut() {
        Player player = playerWithCoins(10);
        Item herb = item("herb", "草药", 10);
        herb.setSellPrice(3);
        PlayerInventory inventory = inventory("herb", 5);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(herb);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);
        when(inventoryMapper.updateById(inventory)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);

        var result = inventoryService.sellItem(1L, "herb", 2, 9L, "sell-herb-1");

        assertEquals(2, result.getSellQuantity());
        assertEquals(3, result.getRemainingQuantity());
        assertEquals(16, player.getCoins());
        ArgumentCaptor<Wrapper<PlayerInventory>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(inventoryMapper).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        var lockOrder = inOrder(playerMapper, inventoryMapper);
        lockOrder.verify(playerMapper).selectByIdForUpdate(1L);
        lockOrder.verify(inventoryMapper).selectOne(any());
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChanges =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        org.mockito.Mockito.verify(resourceTransactionService, org.mockito.Mockito.times(2))
                .record(resourceChanges.capture());
        assertEquals("item", resourceChanges.getAllValues().get(0).resourceType());
        assertEquals("herb", resourceChanges.getAllValues().get(0).resourceKey());
        assertEquals(-2L, resourceChanges.getAllValues().get(0).amount());
        assertEquals(5L, resourceChanges.getAllValues().get(0).balanceBefore());
        assertEquals(3L, resourceChanges.getAllValues().get(0).balanceAfter());
        assertEquals(6L, resourceChanges.getAllValues().get(1).amount());
        assertEquals(10L, resourceChanges.getAllValues().get(1).balanceBefore());
        assertEquals(16L, resourceChanges.getAllValues().get(1).balanceAfter());
        assertEquals(resourceChanges.getAllValues().get(0).transactionGroupId(),
                resourceChanges.getAllValues().get(1).transactionGroupId());
    }

    @Test
    void sellItemRejectsCurrencyOverflowBeforeConsumingTheItem() {
        Player player = playerWithCoins(Integer.MAX_VALUE);
        Item herb = item("herb", "草药", 10);
        herb.setSellPrice(3);
        PlayerInventory inventory = inventory("herb", 5);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(herb);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.sellItem(1L, "herb", 2, 9L, "sell-herb-overflow"));

        assertEquals(ErrorCode.BAD_REQUEST, error.getCode());
        assertEquals(5, inventory.getQuantity());
        assertEquals(Integer.MAX_VALUE, player.getCoins());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(inventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        org.mockito.Mockito.verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void batchSaleAggregatesResultsInsideOneTransactionalServiceMethod() throws NoSuchMethodException {
        Player player = playerWithCoins(10);
        Item herb = item("herb", "草药", 10);
        herb.setSellPrice(3);
        Item ore = item("ore", "矿石", 10);
        ore.setSellPrice(5);
        PlayerInventory herbs = inventory("herb", 5);
        PlayerInventory ores = inventory("ore", 1);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(herb, ore);
        when(inventoryMapper.selectOne(any())).thenReturn(herbs, ores);
        when(inventoryMapper.updateById(herbs)).thenReturn(1);
        when(inventoryMapper.deleteById(ores)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);

        var result = inventoryService.sellItems(1L, java.util.List.of(
                new SellItemCommand("herb", 2), new SellItemCommand("ore", 1)), 9L, "sell-batch-1");

        assertEquals(11, result.get("coins"));
        assertEquals(java.util.List.of("herb", "ore"), result.get("items"));
        assertEquals(21, player.getCoins());
        verify(playerMapper).selectByIdForUpdate(1L);
        assertTrue(InventoryServiceImpl.class.getMethod("sellItems", Long.class,
                java.util.List.class, Long.class, String.class).isAnnotationPresent(Transactional.class));
    }

    @Test
    void batchSalePropagatesAnInvalidLaterItemInsteadOfReportingPartialSuccess() {
        Player player = playerWithCoins(10);
        Item herb = item("herb", "草药", 10);
        herb.setSellPrice(3);
        Item relic = item("relic", "不可出售的遗物", 1);
        relic.setSellPrice(0);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(itemMapper.selectOne(any())).thenReturn(herb, relic);
        when(inventoryMapper.selectOne(any())).thenReturn(inventory("herb", 2));
        when(inventoryMapper.updateById(any(PlayerInventory.class))).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class, () -> inventoryService.sellItems(1L,
                java.util.List.of(new SellItemCommand("herb", 1), new SellItemCommand("relic", 1)),
                9L, "sell-batch-failure-1"));

        assertEquals(ErrorCode.ITEM_CANNOT_SELL, error.getCode());
    }

    @Test
    void synthesisGrantsEquipmentToEquipmentServiceAndReportsItsRealType() {
        Player player = playerWithCoins(100);
        SynthesisRecipe recipe = recipe("craft_blade", "hunter_blade", "{\"scrap\":1}", 20);
        Equipment blade = new Equipment();
        blade.setEquipmentKey("hunter_blade");
        blade.setName("猎人短刃");
        PlayerInventory scrap = inventory("scrap", 1);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(inventoryMapper.selectOne(any())).thenReturn(scrap, scrap);
        when(itemMapper.selectOne(any())).thenReturn(item("scrap", "废料", 10), null);
        when(equipmentMapper.selectOne(any())).thenReturn(null, blade);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.deleteById(scrap)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(equipmentService.addEquipment(1L, "hunter_blade", "synthesis")).thenReturn(1);

        var result = inventoryService.synthesize(1L, "craft_blade", 9L, "synthesis-blade-1");

        assertEquals("equipment", result.get("resultType"));
        assertEquals("猎人短刃", result.get("resultItemName"));
        assertEquals(80, player.getCoins());
        verify(equipmentService).addEquipment(1L, "hunter_blade", "synthesis");
        verify(inventoryMapper).deleteById(scrap);
    }

    @Test
    void synthesisGrantsOrdinaryItemsOnlyAfterMaterialsAndCoinsAreCharged() {
        Player player = playerWithCoins(100);
        SynthesisRecipe recipe = recipe("craft_potion", "potion", "{\"scrap\":1}", 20);
        Item potion = item("potion", "治疗药剂", 10);
        PlayerInventory scrap = inventory("scrap", 1);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(inventoryMapper.selectOne(any())).thenReturn(scrap, null, scrap, null);
        when(itemMapper.selectOne(any())).thenReturn(item("scrap", "废料", 10), potion, potion);
        when(equipmentMapper.selectOne(any())).thenReturn(null);
        when(inventoryMapper.deleteById(scrap)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(inventoryMapper.insert(any(PlayerInventory.class))).thenReturn(1);

        var result = inventoryService.synthesize(1L, "craft_potion", 9L, "synthesis-potion-1");

        assertEquals("item", result.get("resultType"));
        assertEquals("potion", result.get("resultItemKey"));
        assertEquals(1, result.get("resultQuantity"));
        assertEquals(80, player.getCoins());
        verify(inventoryMapper).deleteById(scrap);
        verify(inventoryMapper).insert(any(PlayerInventory.class));
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChanges =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        org.mockito.Mockito.verify(resourceTransactionService, org.mockito.Mockito.times(3))
                .record(resourceChanges.capture());
        var byResource = resourceChanges.getAllValues().stream().collect(
                java.util.stream.Collectors.toMap(
                        change -> change.resourceType() + ":" + change.resourceKey(), change -> change));
        assertEquals(-1L, byResource.get("item:scrap").amount());
        assertEquals(1L, byResource.get("item:scrap").balanceBefore());
        assertEquals(0L, byResource.get("item:scrap").balanceAfter());
        assertEquals(-20L, byResource.get("currency:coins").amount());
        assertEquals(100L, byResource.get("currency:coins").balanceBefore());
        assertEquals(80L, byResource.get("currency:coins").balanceAfter());
        assertEquals(1L, byResource.get("item:potion").amount());
        assertEquals(0L, byResource.get("item:potion").balanceBefore());
        assertEquals(1L, byResource.get("item:potion").balanceAfter());
    }

    @Test
    void synthesisDoesNotConsumeMaterialsWhenEquipmentIsAlreadyOwned() {
        Player player = playerWithCoins(100);
        SynthesisRecipe recipe = recipe("craft_blade", "hunter_blade", "{\"scrap\":1}", 20);
        Equipment blade = new Equipment();
        blade.setEquipmentKey("hunter_blade");
        blade.setName("猎人短刃");
        PlayerInventory scrap = inventory("scrap", 1);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(inventoryMapper.selectOne(any())).thenReturn(scrap);
        when(itemMapper.selectOne(any())).thenReturn(item("scrap", "废料", 10), null);
        when(equipmentMapper.selectOne(any())).thenReturn(null, blade);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(new PlayerEquipment());

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.synthesize(1L, "craft_blade", 9L, "synthesis-blade-2"));

        assertEquals(ErrorCode.EQUIP_ALREADY_OWNED, error.getCode());
        assertEquals(100, player.getCoins());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(equipmentService, never()).addEquipment(any(), any(), any());
    }

    @Test
    void synthesisRejectsFullResultStackBeforeConsumingMaterials() {
        Player player = playerWithCoins(100);
        SynthesisRecipe recipe = recipe("craft_potion", "potion", "{\"scrap\":1}", 20);
        Item potion = item("potion", "治疗药剂", 5);
        PlayerInventory scrap = inventory("scrap", 1);
        PlayerInventory fullPotionStack = inventory("potion", 5);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(inventoryMapper.selectOne(any())).thenReturn(scrap, fullPotionStack);
        when(itemMapper.selectOne(any())).thenReturn(potion);
        when(equipmentMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.synthesize(1L, "craft_potion", 9L, "synthesis-potion-2"));

        assertEquals(ErrorCode.RESOURCE_NOT_ENOUGH, error.getCode());
        verify(inventoryMapper, never()).deleteById(any(PlayerInventory.class));
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void synthesisCanConsumeUnequippedEquipmentIngredients() {
        Player player = playerWithCoins(500);
        SynthesisRecipe recipe = recipe("craft_hunter_blade", "hunter_blade",
                "{\"rusty_dagger\":1,\"hunters_ring\":1}", 150);
        Equipment dagger = equipment("rusty_dagger", "生锈短刀");
        Equipment ring = equipment("hunters_ring", "猎人的戒指");
        Equipment blade = equipment("hunter_blade", "猎人短刃");
        PlayerEquipment ownedDagger = playerEquipment("rusty_dagger", 0, 0);
        PlayerEquipment ownedRing = playerEquipment("hunters_ring", 0, 0);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(itemMapper.selectOne(any())).thenReturn(null, null, null);
        when(equipmentMapper.selectOne(any())).thenReturn(dagger, ring, blade);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(ownedDagger, ownedRing, null);
        when(playerEquipmentMapper.deleteById(ownedDagger)).thenReturn(1);
        when(playerEquipmentMapper.deleteById(ownedRing)).thenReturn(1);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(equipmentService.addEquipment(1L, "hunter_blade", "synthesis")).thenReturn(1);

        var result = inventoryService.synthesize(1L, "craft_hunter_blade", 9L, "synthesis-hunter-1");

        assertEquals("equipment", result.get("resultType"));
        assertEquals("hunter_blade", result.get("resultItemKey"));
        assertEquals(350, player.getCoins());
        verify(playerEquipmentMapper).deleteById(ownedDagger);
        verify(playerEquipmentMapper).deleteById(ownedRing);
        verify(equipmentService).addEquipment(1L, "hunter_blade", "synthesis");
        ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChanges =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        org.mockito.Mockito.verify(resourceTransactionService, org.mockito.Mockito.times(4))
                .record(resourceChanges.capture());
        var byResource = resourceChanges.getAllValues().stream().collect(
                java.util.stream.Collectors.toMap(
                        change -> change.resourceType() + ":" + change.resourceKey()
                                + ":" + change.amount(), change -> change));
        assertEquals(-1L, byResource.get("equipment:rusty_dagger:-1").amount());
        assertEquals(1L, byResource.get("equipment:rusty_dagger:-1").balanceBefore());
        assertEquals(0L, byResource.get("equipment:rusty_dagger:-1").balanceAfter());
        assertEquals(-1L, byResource.get("equipment:hunters_ring:-1").amount());
        assertEquals(-150L, byResource.get("currency:coins:-150").amount());
        assertEquals(1L, byResource.get("equipment:hunter_blade:1").amount());
    }

    @Test
    void synthesisNeverConsumesAnEquippedEquipmentIngredient() {
        Player player = playerWithCoins(500);
        SynthesisRecipe recipe = recipe("craft_hunter_blade", "hunter_blade",
                "{\"rusty_dagger\":1}", 150);
        Equipment dagger = equipment("rusty_dagger", "生锈短刀");
        PlayerEquipment equippedDagger = playerEquipment("rusty_dagger", 1, 0);

        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(player);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(equipmentMapper.selectOne(any())).thenReturn(dagger);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(equippedDagger);

        BusinessException error = assertThrows(BusinessException.class,
                () -> inventoryService.synthesize(1L, "craft_hunter_blade", 9L, "synthesis-hunter-2"));

        assertEquals(ErrorCode.INVALID_FIELD_VALUE, error.getCode());
        assertEquals(500, player.getCoins());
        verify(playerEquipmentMapper, never()).deleteById(any(PlayerEquipment.class));
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(equipmentService, never()).addEquipment(any(), any(), any());
    }

    @Test
    void recipeSummaryUsesNamesForItemAndEquipmentIngredients() {
        SynthesisRecipe recipe = recipe("craft_hunter_blade", "hunter_blade",
                "{\"rusty_dagger\":1,\"story_fragment\":5}", 150);
        Equipment dagger = equipment("rusty_dagger", "生锈短刀");
        Item storyFragment = item("story_fragment", "故事碎片", 99);
        when(recipeMapper.selectList(any())).thenReturn(java.util.List.of(recipe));
        when(itemMapper.selectOne(any())).thenReturn(null, storyFragment, null);
        when(equipmentMapper.selectOne(any())).thenReturn(dagger, null, null);

        var recipes = inventoryService.getRecipes();

        assertEquals("生锈短刀 ×1 + 故事碎片 ×5", recipes.get(0).get("ingredientSummary"));
    }

    private static Item item(int maxStack) {
        return item("herb", "草药", maxStack);
    }

    private static Item item(String itemKey, String name, int maxStack) {
        Item item = new Item();
        item.setItemKey(itemKey);
        item.setName(name);
        item.setEnabled(1);
        item.setMaxStack(maxStack);
        return item;
    }

    private static Equipment equipment(String equipmentKey, String name) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentKey(equipmentKey);
        equipment.setName(name);
        equipment.setEnabled(1);
        return equipment;
    }

    private static PlayerEquipment playerEquipment(String equipmentKey, int equipped, int listed) {
        PlayerEquipment equipment = new PlayerEquipment();
        equipment.setPlayerId(1L);
        equipment.setEquipmentKey(equipmentKey);
        equipment.setEquipped(equipped);
        equipment.setListed(listed);
        return equipment;
    }

    private static Player playerWithCoins(int coins) {
        Player player = new Player();
        player.setId(1L);
        player.setUserId(9L);
        player.setCoins(coins);
        return player;
    }

    private static SynthesisRecipe recipe(String key, String resultKey, String ingredients, int costCoins) {
        SynthesisRecipe recipe = new SynthesisRecipe();
        recipe.setRecipeKey(key);
        recipe.setName("合成测试产物");
        recipe.setResultItemKey(resultKey);
        recipe.setResultQuantity(1);
        recipe.setIngredientsJson(ingredients);
        recipe.setCostCoins(costCoins);
        return recipe;
    }

    private static PlayerInventory inventory(String itemKey, int quantity) {
        PlayerInventory inventory = new PlayerInventory();
        inventory.setPlayerId(1L);
        inventory.setItemKey(itemKey);
        inventory.setQuantity(quantity);
        return inventory;
    }
}
