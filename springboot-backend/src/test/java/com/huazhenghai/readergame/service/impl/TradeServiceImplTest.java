package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Equipment;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerEquipment;
import com.huazhenghai.readergame.entity.PlayerInventory;
import com.huazhenghai.readergame.entity.TradeListing;
import com.huazhenghai.readergame.entity.TradeRecord;
import com.huazhenghai.readergame.mapper.EquipmentMapper;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.PlayerEquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.TradeListingMapper;
import com.huazhenghai.readergame.mapper.TradeRecordMapper;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.vo.TradeBuyResultVO;
import com.huazhenghai.readergame.vo.TradeListingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock private TradeListingMapper tradeListingMapper;
    @Mock private TradeRecordMapper tradeRecordMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerInventoryMapper playerInventoryMapper;
    @Mock private PlayerEquipmentMapper playerEquipmentMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private QuestService questService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private PlayerActionReceiptService actionReceiptService;

    private TradeServiceImpl tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeServiceImpl(tradeListingMapper, tradeRecordMapper, playerMapper,
                playerInventoryMapper, playerEquipmentMapper, playerLogMapper, itemMapper,
                equipmentMapper, questService, resourceTransactionService, actionReceiptService);
    }

    @Test
    void itemListingMovesQuantityIntoEscrowAndRecordsTheDebit() {
        Player seller = player(1L, 10);
        PlayerInventory inventory = inventory(1L, "herb", 5);
        Item herb = item("herb", "灵草", 10);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(seller);
        when(playerMapper.selectById(1L)).thenReturn(seller);
        when(itemMapper.selectOne(any())).thenReturn(herb);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inventory);
        when(playerInventoryMapper.updateById(inventory)).thenReturn(1);
        when(tradeListingMapper.insert(any(TradeListing.class))).thenAnswer(invocation -> {
            ((TradeListing) invocation.getArgument(0)).setId(101L);
            return 1;
        });

        var listing = tradeService.createItemListing(1L, "herb", 2, 15, "list-item-1");

        assertEquals(30, listing.getTotalPrice());
        assertEquals(3, inventory.getQuantity());
        ArgumentCaptor<PlayerResourceTransactionService.Change> change =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(change.capture());
        assertEquals(1L, change.getValue().playerId());
        assertEquals("item", change.getValue().resourceType());
        assertEquals("herb", change.getValue().resourceKey());
        assertEquals(-2L, change.getValue().amount());
        assertEquals(5L, change.getValue().balanceBefore());
        assertEquals(3L, change.getValue().balanceAfter());
        assertEquals("market_escrow", change.getValue().sourceType());
    }

    @Test
    void cancellingItemListingReturnsEscrowAndRecordsTheCredit() {
        TradeListing listing = itemListing("TL-CANCEL-1", 1L, 2, 30);
        Player seller = player(1L, 10);
        PlayerInventory inventory = inventory(1L, "herb", 3);
        Item herb = item("herb", "灵草", 10);
        when(tradeListingMapper.selectOne(any())).thenReturn(listing);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(seller);
        when(playerMapper.selectById(1L)).thenReturn(seller);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inventory);
        when(playerInventoryMapper.updateById(inventory)).thenReturn(1);
        when(itemMapper.selectOne(any())).thenReturn(herb);
        when(tradeListingMapper.updateById(listing)).thenReturn(1);

        tradeService.cancelListing(1L, "TL-CANCEL-1", "cancel-1");

        assertEquals(5, inventory.getQuantity());
        assertEquals("cancelled", listing.getStatus());
        ArgumentCaptor<PlayerResourceTransactionService.Change> change =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService).record(change.capture());
        assertEquals(2L, change.getValue().amount());
        assertEquals(3L, change.getValue().balanceBefore());
        assertEquals(5L, change.getValue().balanceAfter());
        assertEquals("market_cancel", change.getValue().sourceType());
    }

    @Test
    void buyingItemTransfersCurrencyAndAssetInOneLedgerGroup() {
        TradeListing listing = itemListing("TL-BUY-ITEM", 2L, 2, 50);
        Player buyer = player(1L, 100);
        Player seller = player(2L, 20);
        PlayerInventory buyerInventory = inventory(1L, "herb", 3);
        Item herb = item("herb", "灵草", 10);
        when(tradeListingMapper.selectOne(any())).thenReturn(listing);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(buyer);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(seller);
        when(playerMapper.updateById(any(Player.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(buyerInventory);
        when(playerInventoryMapper.updateById(buyerInventory)).thenReturn(1);
        when(itemMapper.selectOne(any())).thenReturn(herb);
        when(tradeListingMapper.updateById(listing)).thenReturn(1);
        when(tradeRecordMapper.insert(any(TradeRecord.class))).thenReturn(1);

        var receipt = tradeService.buyListing(1L, "TL-BUY-ITEM", "buy-item-1");

        assertEquals(50, receipt.getNewBuyerCoins());
        assertEquals(5, buyerInventory.getQuantity());
        assertEquals(70, seller.getCoins());
        ArgumentCaptor<PlayerResourceTransactionService.Change> changes =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService, org.mockito.Mockito.times(3)).record(changes.capture());
        Map<String, PlayerResourceTransactionService.Change> byPlayerAndResource =
                changes.getAllValues().stream().collect(Collectors.toMap(
                        change -> change.playerId() + ":" + change.resourceType()
                                + ":" + change.resourceKey(), change -> change));
        assertEquals(-50L, byPlayerAndResource.get("1:currency:coins").amount());
        assertEquals(100L, byPlayerAndResource.get("1:currency:coins").balanceBefore());
        assertEquals(50L, byPlayerAndResource.get("1:currency:coins").balanceAfter());
        assertEquals(50L, byPlayerAndResource.get("2:currency:coins").amount());
        assertEquals(20L, byPlayerAndResource.get("2:currency:coins").balanceBefore());
        assertEquals(70L, byPlayerAndResource.get("2:currency:coins").balanceAfter());
        assertEquals(2L, byPlayerAndResource.get("1:item:herb").amount());
        assertEquals(3L, byPlayerAndResource.get("1:item:herb").balanceBefore());
        assertEquals(5L, byPlayerAndResource.get("1:item:herb").balanceAfter());
        assertEquals(1L, changes.getAllValues().stream()
                .map(PlayerResourceTransactionService.Change::transactionGroupId).distinct().count());
        assertEquals("market:TL-BUY-ITEM", changes.getAllValues().get(0).transactionGroupId());
        verify(questService).addProgress(1L, "trade_buy_count", 1, "trade", "TL-BUY-ITEM");
        verify(questService).addProgress(2L, "trade_sell_count", 1, "trade", "TL-BUY-ITEM");
    }

    @Test
    void buyingEquipmentTransfersOwnershipAndCurrencyToBothPlayers() {
        TradeListing listing = new TradeListing();
        listing.setListingNo("TL-BUY-EQUIP");
        listing.setSellerPlayerId(2L);
        listing.setListingType("equipment");
        listing.setEquipmentKey("old_blade");
        listing.setQuantity(1);
        listing.setTotalPrice(40);
        listing.setStatus("active");
        Player buyer = player(1L, 100);
        Player seller = player(2L, 0);
        PlayerEquipment equipment = new PlayerEquipment();
        equipment.setPlayerId(2L);
        equipment.setEquipmentKey("old_blade");
        equipment.setListed(1);
        equipment.setEquipped(0);
        Equipment definition = equipment("old_blade", "旧刃");
        when(tradeListingMapper.selectOne(any())).thenReturn(listing);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(buyer);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(seller);
        when(playerMapper.updateById(any(Player.class))).thenReturn(1);
        when(playerEquipmentMapper.selectOne(any())).thenReturn(equipment);
        when(playerEquipmentMapper.updateById(equipment)).thenReturn(1);
        when(equipmentMapper.selectOne(any())).thenReturn(definition);
        when(tradeListingMapper.updateById(listing)).thenReturn(1);
        when(tradeRecordMapper.insert(any(TradeRecord.class))).thenReturn(1);

        tradeService.buyListing(1L, "TL-BUY-EQUIP", "buy-equip-1");

        assertEquals(1L, equipment.getPlayerId());
        assertEquals(0, equipment.getListed());
        ArgumentCaptor<PlayerResourceTransactionService.Change> changes =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService, org.mockito.Mockito.times(4)).record(changes.capture());
        Map<String, PlayerResourceTransactionService.Change> byPlayerAndResource =
                changes.getAllValues().stream().collect(Collectors.toMap(
                        change -> change.playerId() + ":" + change.resourceType()
                                + ":" + change.resourceKey(), change -> change));
        assertEquals(-1L, byPlayerAndResource.get("2:equipment:old_blade").amount());
        assertEquals(1L, byPlayerAndResource.get("1:equipment:old_blade").amount());
        assertEquals(1L, changes.getAllValues().stream()
                .map(PlayerResourceTransactionService.Change::transactionGroupId).distinct().count());
    }

    @Test
    void fullSellerStackPreventsCancellingListingWithoutLosingEscrow() {
        TradeListing listing = itemListing("TL-CANCEL-FULL", 1L, 2, 30);
        Player seller = player(1L, 10);
        PlayerInventory inventory = inventory(1L, "herb", 9);
        Item herb = item("herb", "灵草", 10);
        when(tradeListingMapper.selectOne(any())).thenReturn(listing);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(seller);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inventory);
        when(itemMapper.selectOne(any())).thenReturn(herb);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> tradeService.cancelListing(1L, "TL-CANCEL-FULL", "cancel-full-1"));

        assertEquals(ErrorCode.RESOURCE_NOT_ENOUGH, exception.getCode());
        assertEquals("active", listing.getStatus());
        assertEquals(9, inventory.getQuantity());
        verify(tradeListingMapper, never()).updateById(any(TradeListing.class));
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void replayedItemListingReturnsSavedReceiptWithoutEscrowingAgain() {
        Player seller = player(1L, 10);
        TradeListingVO savedReceipt = new TradeListingVO();
        savedReceipt.setListingNo("TL-SAVED-1");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(seller);
        when(actionReceiptService.findReplay(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("trade.list.item"),
                org.mockito.ArgumentMatchers.eq("list-replay-1"), any(),
                org.mockito.ArgumentMatchers.eq(TradeListingVO.class))).thenReturn(savedReceipt);

        TradeListingVO result = tradeService.createItemListing(1L, "herb", 2, 15, "list-replay-1");

        assertEquals("TL-SAVED-1", result.getListingNo());
        verify(playerInventoryMapper, never()).selectOne(any());
        verify(playerInventoryMapper, never()).updateById(any(PlayerInventory.class));
        verify(tradeListingMapper, never()).insert(any(TradeListing.class));
        verify(resourceTransactionService, never()).record(any());
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void replayedPurchaseReturnsSavedReceiptEvenAfterListingWasMarkedSold() {
        TradeListing listing = itemListing("TL-BUY-REPLAY", 2L, 2, 50);
        listing.setStatus("sold");
        Player buyer = player(1L, 50);
        Player seller = player(2L, 70);
        TradeBuyResultVO savedReceipt = new TradeBuyResultVO();
        savedReceipt.setListingNo("TL-BUY-REPLAY");
        savedReceipt.setNewBuyerCoins(50);
        when(tradeListingMapper.selectOne(any())).thenReturn(listing);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(buyer);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(seller);
        when(actionReceiptService.findReplay(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("trade.buy"),
                org.mockito.ArgumentMatchers.eq("buy-replay-1"), any(),
                org.mockito.ArgumentMatchers.eq(TradeBuyResultVO.class))).thenReturn(savedReceipt);

        TradeBuyResultVO result = tradeService.buyListing(1L, "TL-BUY-REPLAY", "buy-replay-1");

        assertEquals("TL-BUY-REPLAY", result.getListingNo());
        assertEquals(50, buyer.getCoins());
        assertEquals(70, seller.getCoins());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(tradeRecordMapper, never()).insert(any(TradeRecord.class));
        verify(resourceTransactionService, never()).record(any());
        verify(questService, never()).addProgress(any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void replayedCancellationReturnsSavedReceiptWithoutReturningEscrowTwice() {
        TradeListing listing = itemListing("TL-CANCEL-REPLAY", 1L, 2, 30);
        listing.setStatus("cancelled");
        Player seller = player(1L, 10);
        TradeListingVO savedReceipt = new TradeListingVO();
        savedReceipt.setListingNo("TL-CANCEL-REPLAY");
        savedReceipt.setStatus("cancelled");
        when(tradeListingMapper.selectOne(any())).thenReturn(listing);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(seller);
        when(actionReceiptService.findReplay(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("trade.cancel"),
                org.mockito.ArgumentMatchers.eq("cancel-replay-1"), any(),
                org.mockito.ArgumentMatchers.eq(TradeListingVO.class))).thenReturn(savedReceipt);

        TradeListingVO result = tradeService.cancelListing(1L, "TL-CANCEL-REPLAY", "cancel-replay-1");

        assertEquals("cancelled", result.getStatus());
        verify(playerInventoryMapper, never()).selectOne(any());
        verify(tradeListingMapper, never()).updateById(any(TradeListing.class));
        verify(resourceTransactionService, never()).record(any());
    }

    private static TradeListing itemListing(String listingNo, long sellerId, int quantity, int totalPrice) {
        TradeListing listing = new TradeListing();
        listing.setListingNo(listingNo);
        listing.setSellerPlayerId(sellerId);
        listing.setListingType("item");
        listing.setItemKey("herb");
        listing.setQuantity(quantity);
        listing.setUnitPrice(totalPrice / quantity);
        listing.setTotalPrice(totalPrice);
        listing.setStatus("active");
        return listing;
    }

    private static Player player(long id, int coins) {
        Player player = new Player();
        player.setId(id);
        player.setPlayerName("player-" + id);
        player.setCoins(coins);
        return player;
    }

    private static PlayerInventory inventory(long playerId, String itemKey, int quantity) {
        PlayerInventory inventory = new PlayerInventory();
        inventory.setPlayerId(playerId);
        inventory.setItemKey(itemKey);
        inventory.setQuantity(quantity);
        return inventory;
    }

    private static Item item(String key, String name, int maxStack) {
        Item item = new Item();
        item.setItemKey(key);
        item.setName(name);
        item.setEnabled(1);
        item.setMaxStack(maxStack);
        return item;
    }

    private static Equipment equipment(String key, String name) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentKey(key);
        equipment.setName(name);
        equipment.setEnabled(1);
        return equipment;
    }
}
