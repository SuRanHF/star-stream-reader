package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Friend;
import com.huazhenghai.readergame.entity.FriendRequest;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerInventory;
import com.huazhenghai.readergame.mapper.FriendMapper;
import com.huazhenghai.readergame.mapper.FriendRequestMapper;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    @Mock private FriendMapper friendMapper;
    @Mock private FriendRequestMapper friendRequestMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private OnlinePlayerService onlinePlayerService;
    @Mock private ItemMapper itemMapper;
    @Mock private PlayerInventoryMapper playerInventoryMapper;
    @Mock private InventoryService inventoryService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;
    @Mock private PlayerActionReceiptService actionReceiptService;

    private FriendServiceImpl friendService;

    @BeforeEach
    void setUp() {
        friendService = new FriendServiceImpl(friendMapper, friendRequestMapper, playerMapper,
                playerLogMapper, onlinePlayerService, new ObjectMapper(), itemMapper,
                playerInventoryMapper, inventoryService, resourceTransactionService, actionReceiptService);
    }

    @Test
    void successfulGiftRefreshesBothFriendRowsUsedByRecentInteractions() {
        Player sender = player(1L, "赠礼者");
        Player target = player(2L, "收礼者");
        Item item = new Item();
        item.setItemKey("herb");
        item.setName("灵草");
        item.setMaxStack(99);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(sender);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(target);
        when(friendMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.selectOne(any())).thenReturn(item);
        PlayerInventory senderInventory = inventory("herb", 5);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null, senderInventory);
        when(inventoryService.removeItem(1L, "herb", 2)).thenReturn(true);
        when(inventoryService.addItemWithResult(2L, "herb", 2)).thenReturn(new ItemGrantResult(2, 2));

        var result = friendService.sendGift(1L, 2L, "herb", 2, "gift-1");

        assertEquals("礼物已送达", result.get("message"));
        verify(friendMapper).update(any(Friend.class), any(QueryWrapper.class));
        org.mockito.ArgumentCaptor<PlayerResourceTransactionService.Change> resourceChanges =
                org.mockito.ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        org.mockito.Mockito.verify(resourceTransactionService, org.mockito.Mockito.times(2))
                .record(resourceChanges.capture());
        assertEquals(-2L, resourceChanges.getAllValues().get(0).amount());
        assertEquals(5L, resourceChanges.getAllValues().get(0).balanceBefore());
        assertEquals(3L, resourceChanges.getAllValues().get(0).balanceAfter());
        assertEquals(2L, resourceChanges.getAllValues().get(1).amount());
        assertEquals(0L, resourceChanges.getAllValues().get(1).balanceBefore());
        assertEquals(2L, resourceChanges.getAllValues().get(1).balanceAfter());
        assertEquals(resourceChanges.getAllValues().get(0).transactionGroupId(),
                resourceChanges.getAllValues().get(1).transactionGroupId());
        assertEquals(1L, resourceChanges.getAllValues().get(0).playerId());
        assertEquals(2L, resourceChanges.getAllValues().get(1).playerId());
    }

    @Test
    void failedGiftDoesNotRefreshRecentInteractionTime() {
        Player sender = player(1L, "赠礼者");
        Player target = player(2L, "收礼者");
        Item item = new Item();
        item.setName("灵草");
        item.setMaxStack(99);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(sender);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(target);
        when(friendMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null, inventory("herb", 2));
        when(inventoryService.removeItem(1L, "herb", 1)).thenReturn(true);
        when(inventoryService.addItemWithResult(2L, "herb", 1)).thenReturn(new ItemGrantResult(0, 99));

        assertThrows(BusinessException.class, () -> friendService.sendGift(1L, 2L, "herb", 1, "gift-fail-1"));

        verify(friendMapper, never()).update(any(Friend.class), any(QueryWrapper.class));
        verify(resourceTransactionService, never()).record(any());
    }

    @Test
    void replayedGiftReturnsSavedReceiptWithoutTransferringItemsAgain() {
        Player sender = player(1L, "赠礼者");
        Player target = player(2L, "收礼者");
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(sender);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(target);
        Map<String, Object> savedReceipt = Map.of("message", "礼物已送达", "quantity", 2);
        when(actionReceiptService.findReplay(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("friend.gift"),
                org.mockito.ArgumentMatchers.eq("gift-replay-1"), any(),
                org.mockito.ArgumentMatchers.eq(Map.class))).thenReturn(savedReceipt);

        var result = friendService.sendGift(1L, 2L, "herb", 2, "gift-replay-1");

        assertEquals("礼物已送达", result.get("message"));
        assertEquals(2, result.get("quantity"));
        verify(friendMapper, never()).selectCount(any());
        verify(inventoryService, never()).removeItem(anyLong(), anyString(), anyInt());
        verify(inventoryService, never()).addItemWithResult(anyLong(), anyString(), anyInt());
        verify(resourceTransactionService, never()).record(any());
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void acceptDoesNotOverwriteARequestAlreadyResolvedByAnotherAction() {
        when(friendRequestMapper.selectByIdForUpdate(91L))
                .thenReturn(request(91L, 1L, 2L, "rejected"));

        assertThrows(BusinessException.class, () -> friendService.acceptRequest(2L, 91L));

        verify(friendRequestMapper).selectByIdForUpdate(91L);
        verify(friendRequestMapper, never()).updateById(any(FriendRequest.class));
    }

    @Test
    void rejectDoesNotOverwriteARequestAlreadyResolvedByAnotherAction() {
        when(friendRequestMapper.selectByIdForUpdate(92L))
                .thenReturn(request(92L, 1L, 2L, "accepted"));

        assertThrows(BusinessException.class, () -> friendService.rejectRequest(2L, 92L));

        verify(friendRequestMapper).selectByIdForUpdate(92L);
        verify(friendRequestMapper, never()).updateById(any(FriendRequest.class));
    }

    @Test
    void cancelDoesNotOverwriteARequestAlreadyResolvedByAnotherAction() {
        when(friendRequestMapper.selectByIdForUpdate(93L))
                .thenReturn(request(93L, 1L, 2L, "accepted"));

        assertThrows(BusinessException.class, () -> friendService.cancelRequest(1L, 93L));

        verify(friendRequestMapper).selectByIdForUpdate(93L);
        verify(friendRequestMapper, never()).updateById(any(FriendRequest.class));
    }

    private static Player player(long id, String name) {
        Player player = new Player();
        player.setId(id);
        player.setPlayerName(name);
        return player;
    }

    private static PlayerInventory inventory(String itemKey, int quantity) {
        PlayerInventory inventory = new PlayerInventory();
        inventory.setPlayerId(1L);
        inventory.setItemKey(itemKey);
        inventory.setQuantity(quantity);
        return inventory;
    }

    private static FriendRequest request(long id, long fromPlayerId, long toPlayerId, String status) {
        FriendRequest request = new FriendRequest();
        request.setId(id);
        request.setFromPlayerId(fromPlayerId);
        request.setToPlayerId(toPlayerId);
        request.setStatus(status);
        return request;
    }
}
