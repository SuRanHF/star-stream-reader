package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Friend;
import com.huazhenghai.readergame.entity.FriendRequest;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerInventory;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.FriendMapper;
import com.huazhenghai.readergame.mapper.FriendRequestMapper;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.FriendService;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.FriendRequestVO;
import com.huazhenghai.readergame.vo.FriendSummaryVO;
import com.huazhenghai.readergame.vo.FriendVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FriendServiceImpl implements FriendService {

    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final OnlinePlayerService onlinePlayerService;
    private final ObjectMapper objectMapper;
    private final ItemMapper itemMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final InventoryService inventoryService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final PlayerActionReceiptService actionReceiptService;

    public FriendServiceImpl(FriendMapper friendMapper,
                             FriendRequestMapper friendRequestMapper,
                             PlayerMapper playerMapper,
                             PlayerLogMapper playerLogMapper,
                             OnlinePlayerService onlinePlayerService,
                             ObjectMapper objectMapper,
                             ItemMapper itemMapper,
                             PlayerInventoryMapper playerInventoryMapper,
                             InventoryService inventoryService,
                             PlayerResourceTransactionService resourceTransactionService,
                             PlayerActionReceiptService actionReceiptService) {
        this.friendMapper = friendMapper;
        this.friendRequestMapper = friendRequestMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.onlinePlayerService = onlinePlayerService;
        this.objectMapper = objectMapper;
        this.itemMapper = itemMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.inventoryService = inventoryService;
        this.resourceTransactionService = resourceTransactionService;
        this.actionReceiptService = actionReceiptService;
    }

    @Override
    public List<Map<String, Object>> searchPlayers(String keyword, Long currentPlayerId) {
        QueryWrapper<Player> qw = new QueryWrapper<>();
        qw.like("player_name", keyword)
          .ne("id", currentPlayerId)
          .last("LIMIT 20");
        List<Player> players = playerMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Player p : players) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("playerId", p.getId());
            entry.put("playerName", p.getPlayerName());
            Map<String, Object> stats = parseStatsJson(p.getStatsJson());
            entry.put("level", toInt(stats.get("level"), 1));
            entry.put("avatarRank", stats.getOrDefault("avatarRank", "F"));
            entry.put("isOnline", onlinePlayerService.isOnline(p.getId()));
            result.add(entry);
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> sendRequest(Long fromPlayerId, Long toPlayerId) {
        if (fromPlayerId.equals(toPlayerId)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "不能添加自己为好友");
        }

        Player target = playerMapper.selectById(toPlayerId);
        if (target == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "目标玩家不存在");
        }

        // Check already friends
        QueryWrapper<Friend> fq = new QueryWrapper<>();
        fq.eq("player_id", fromPlayerId)
          .eq("friend_id", toPlayerId)
          .eq("deleted", 0);
        if (friendMapper.selectCount(fq) > 0) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已经是好友");
        }

        // Check existing request from me to target (any status)
        QueryWrapper<FriendRequest> rq = new QueryWrapper<>();
        rq.eq("from_player_id", fromPlayerId)
          .eq("to_player_id", toPlayerId);
        FriendRequest existingRequest = friendRequestMapper.selectOne(rq);

        if (existingRequest != null) {
            if ("pending".equals(existingRequest.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已发送过好友申请，等待对方回应");
            }
            // Re-send: update previous accepted/rejected request back to pending
            existingRequest.setStatus("pending");
            existingRequest.setUpdatedAt(LocalDateTime.now());
            friendRequestMapper.updateById(existingRequest);

            Player fromPlayer = playerMapper.selectById(fromPlayerId);
            writeLog(toPlayerId, "friend", (fromPlayer != null ? fromPlayer.getPlayerName() : "未知化身") + " 向你发送了好友申请");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "pending");
            result.put("message", "好友申请已发送");
            return result;
        }

        // Check if target already sent a request to me → auto-accept
        QueryWrapper<FriendRequest> reverseQ = new QueryWrapper<>();
        reverseQ.eq("from_player_id", toPlayerId)
                .eq("to_player_id", fromPlayerId)
                .eq("status", "pending");
        FriendRequest reverse = friendRequestMapper.selectOne(reverseQ);
        if (reverse != null) {
            reverse.setStatus("accepted");
            reverse.setUpdatedAt(LocalDateTime.now());
            friendRequestMapper.updateById(reverse);

            createBidirectionalFriendship(fromPlayerId, toPlayerId);

            Player fromPlayer = playerMapper.selectById(fromPlayerId);
            writeLog(fromPlayerId, "friend", "与 " + target.getPlayerName() + " 成为好友");
            writeLog(toPlayerId, "friend", "与 " + (fromPlayer != null ? fromPlayer.getPlayerName() : "未知化身") + " 成为好友");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "accepted");
            result.put("message", "好友申请已自动接受（对方已向你发送过申请）");
            return result;
        }

        // Create new request
        FriendRequest fr = new FriendRequest();
        fr.setFromPlayerId(fromPlayerId);
        fr.setToPlayerId(toPlayerId);
        fr.setStatus("pending");
        fr.setCreatedAt(LocalDateTime.now());
        fr.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.insert(fr);

        Player fromPlayer = playerMapper.selectById(fromPlayerId);
        writeLog(toPlayerId, "friend", (fromPlayer != null ? fromPlayer.getPlayerName() : "未知化身") + " 向你发送了好友申请");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "pending");
        result.put("message", "好友申请已发送");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> acceptRequest(Long playerId, Long requestId) {
        FriendRequest fr = friendRequestMapper.selectByIdForUpdate(requestId);
        if (fr == null || !fr.getToPlayerId().equals(playerId) || !"pending".equals(fr.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "好友申请不存在或已过期");
        }

        fr.setStatus("accepted");
        fr.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.updateById(fr);

        createBidirectionalFriendship(fr.getFromPlayerId(), playerId);

        Player fromPlayer = playerMapper.selectById(fr.getFromPlayerId());
        Player toPlayer = playerMapper.selectById(playerId);
        writeLog(playerId, "friend", "与 " + (fromPlayer != null ? fromPlayer.getPlayerName() : "对方") + " 成为好友");
        writeLog(fr.getFromPlayerId(), "friend", "与 " + (toPlayer != null ? toPlayer.getPlayerName() : "未知化身") + " 成为好友");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已接受好友申请");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> rejectRequest(Long playerId, Long requestId) {
        FriendRequest fr = friendRequestMapper.selectByIdForUpdate(requestId);
        if (fr == null || !fr.getToPlayerId().equals(playerId) || !"pending".equals(fr.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "好友申请不存在");
        }

        fr.setStatus("rejected");
        fr.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.updateById(fr);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已拒绝好友申请");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelRequest(Long playerId, Long requestId) {
        FriendRequest fr = friendRequestMapper.selectByIdForUpdate(requestId);
        if (fr == null || !fr.getFromPlayerId().equals(playerId) || !"pending".equals(fr.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申请不存在或已处理");
        }

        fr.setStatus("rejected");
        fr.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.updateById(fr);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已取消好友申请");
        return result;
    }

    @Override
    public List<FriendVO> getFriends(Long playerId) {
        QueryWrapper<Friend> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId)
          .eq("deleted", 0)
          .orderByDesc("created_at");
        List<Friend> friends = friendMapper.selectList(qw);

        List<FriendVO> result = new ArrayList<>();
        for (Friend f : friends) {
            Player friendPlayer = playerMapper.selectById(f.getFriendId());
            if (friendPlayer == null) continue;

            FriendVO vo = new FriendVO();
            vo.setId(f.getId());
            vo.setPlayerId(friendPlayer.getId());
            vo.setPlayerName(friendPlayer.getPlayerName());
            Map<String, Object> stats = parseStatsJson(friendPlayer.getStatsJson());
            vo.setLevel(toInt(stats.get("level"), 1));
            vo.setAvatarRank(stats.getOrDefault("avatarRank", "F").toString());
            vo.setIsOnline(onlinePlayerService.isOnline(friendPlayer.getId()));
            vo.setCreatedAt(f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> removeFriend(Long playerId, Long friendPlayerId) {
        // Soft-delete both directions
        QueryWrapper<Friend> qw1 = new QueryWrapper<>();
        qw1.eq("player_id", playerId)
           .eq("friend_id", friendPlayerId)
           .eq("deleted", 0);
        Friend f1 = new Friend();
        f1.setDeleted(1);
        f1.setUpdatedAt(LocalDateTime.now());
        friendMapper.update(f1, qw1);

        QueryWrapper<Friend> qw2 = new QueryWrapper<>();
        qw2.eq("player_id", friendPlayerId)
           .eq("friend_id", playerId)
           .eq("deleted", 0);
        Friend f2 = new Friend();
        f2.setDeleted(1);
        f2.setUpdatedAt(LocalDateTime.now());
        friendMapper.update(f2, qw2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已删除好友");
        return result;
    }

    @Override
    public List<FriendRequestVO> getPendingRequests(Long playerId) {
        QueryWrapper<FriendRequest> qw = new QueryWrapper<>();
        qw.eq("to_player_id", playerId)
          .eq("status", "pending")
          .orderByDesc("created_at");
        List<FriendRequest> requests = friendRequestMapper.selectList(qw);

        List<FriendRequestVO> result = new ArrayList<>();
        for (FriendRequest fr : requests) {
            FriendRequestVO vo = new FriendRequestVO();
            vo.setId(fr.getId());
            vo.setFromPlayerId(fr.getFromPlayerId());
            Player fromPlayer = playerMapper.selectById(fr.getFromPlayerId());
            vo.setFromPlayerName(fromPlayer != null ? fromPlayer.getPlayerName() : "未知化身");
            vo.setStatus(fr.getStatus());
            vo.setCreatedAt(fr.getCreatedAt() != null ? fr.getCreatedAt().toString() : null);
            result.add(vo);
        }
        return result;
    }

    @Override
    public FriendSummaryVO getFriendSummary(Long playerId) {
        QueryWrapper<Friend> fq = new QueryWrapper<>();
        fq.eq("player_id", playerId).eq("deleted", 0);
        int friendCount = friendMapper.selectCount(fq).intValue();

        QueryWrapper<FriendRequest> rq = new QueryWrapper<>();
        rq.eq("to_player_id", playerId).eq("status", "pending");
        int pendingCount = friendRequestMapper.selectCount(rq).intValue();

        FriendSummaryVO vo = new FriendSummaryVO();
        vo.setFriendCount(friendCount);
        vo.setPendingRequestCount(pendingCount);
        return vo;
    }

    @Override
    @Transactional
    public Map<String, Object> sendGift(Long playerId, Long targetPlayerId, String itemKey,
                                        int quantity, String requestKey) {
        if (requestKey == null || requestKey.isBlank() || requestKey.trim().length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 必须为 1 到 64 个字符");
        }
        if (targetPlayerId == null || playerId.equals(targetPlayerId)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "不能向自己赠送物品");
        }
        if (itemKey == null || itemKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_FIELDS, "缺少参数: itemKey");
        }
        if (quantity <= 0 || quantity > 99) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "赠送数量必须在 1 到 99 之间");
        }

        Player sender;
        Player target;
        if (playerId < targetPlayerId) {
            sender = playerMapper.selectByIdForUpdate(playerId);
            target = playerMapper.selectByIdForUpdate(targetPlayerId);
        } else {
            target = playerMapper.selectByIdForUpdate(targetPlayerId);
            sender = playerMapper.selectByIdForUpdate(playerId);
        }
        if (sender == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "赠礼玩家不存在");
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "目标玩家不存在");
        }

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("targetPlayerId", targetPlayerId);
        requestPayload.put("itemKey", itemKey);
        requestPayload.put("quantity", quantity);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = (Map<String, Object>) actionReceiptService.findReplay(
                playerId, "friend.gift", requestKey, requestPayload, Map.class);
        if (replay != null) return replay;

        QueryWrapper<Friend> friendship = new QueryWrapper<>();
        friendship.eq("player_id", playerId)
                .eq("friend_id", targetPlayerId)
                .eq("deleted", 0);
        if (friendMapper.selectCount(friendship) == 0) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "只有好友之间可以赠送物品");
        }

        Item item = itemMapper.selectOne(new QueryWrapper<Item>()
                .eq("item_key", itemKey)
                .eq("enabled", 1));
        if (item == null) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "物品不存在: " + itemKey);
        }

        QueryWrapper<PlayerInventory> targetInventoryQuery = new QueryWrapper<>();
        targetInventoryQuery.eq("player_id", targetPlayerId).eq("item_key", itemKey);
        targetInventoryQuery.last("FOR UPDATE");
        PlayerInventory targetInventory = playerInventoryMapper.selectOne(targetInventoryQuery);
        int targetQuantity = targetInventory == null || targetInventory.getQuantity() == null
                ? 0 : targetInventory.getQuantity();
        int maxStack = item.getMaxStack() == null ? 999 : item.getMaxStack();
        if ((long) targetQuantity + quantity > maxStack) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "对方背包没有足够的堆叠空间");
        }

        QueryWrapper<PlayerInventory> senderInventoryQuery = new QueryWrapper<>();
        senderInventoryQuery.eq("player_id", playerId).eq("item_key", itemKey);
        senderInventoryQuery.last("FOR UPDATE");
        PlayerInventory senderInventory = playerInventoryMapper.selectOne(senderInventoryQuery);
        int senderQuantity = senderInventory == null || senderInventory.getQuantity() == null
                ? 0 : senderInventory.getQuantity();
        if (senderQuantity < quantity) {
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH,
                    "你的背包中没有足够的物品: " + item.getName());
        }

        if (!inventoryService.removeItem(playerId, itemKey, quantity)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "你的背包中没有足够的物品: " + item.getName());
        }
        var giftGrant = inventoryService.addItemWithResult(targetPlayerId, itemKey, quantity);
        if (giftGrant.quantityGranted() != quantity) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "礼物没有成功送入对方背包，请稍后重试");
        }

        int senderAfter = senderQuantity - quantity;
        int targetAfter = giftGrant.totalQuantity();
        int targetBefore = targetAfter - giftGrant.quantityGranted();
        if (targetBefore != targetQuantity) {
            throw new IllegalStateException("赠礼前后接收方背包数量不一致");
        }
        String transactionGroupId = UUID.randomUUID().toString();
        Map<String, Object> resourceMetadata = Map.of(
                "itemKey", itemKey, "quantity", quantity,
                "senderPlayerId", playerId, "targetPlayerId", targetPlayerId);
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, "item", itemKey, -quantity, senderQuantity, senderAfter,
                "friend_gift", playerId + "->" + targetPlayerId, transactionGroupId,
                transactionGroupId + ":sender:item:" + itemKey, resourceMetadata));
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                targetPlayerId, "item", itemKey, quantity, targetBefore, targetAfter,
                "friend_gift", playerId + "->" + targetPlayerId, transactionGroupId,
                transactionGroupId + ":recipient:item:" + itemKey, resourceMetadata));

        markFriendInteraction(playerId, targetPlayerId);

        String senderName = sender == null ? "未知化身" : sender.getPlayerName();
        writeLog(playerId, "friend_gift", "赠送了 " + quantity + " 个 " + item.getName() + " 给 " + target.getPlayerName());
        writeLog(targetPlayerId, "friend_gift", senderName + " 赠送了 " + quantity + " 个 " + item.getName() + " 给你");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemKey", itemKey);
        result.put("itemName", item.getName());
        result.put("quantity", quantity);
        result.put("targetPlayerId", targetPlayerId);
        result.put("targetPlayerName", target.getPlayerName());
        result.put("message", "礼物已送达");
        actionReceiptService.record(playerId, "friend.gift", requestKey, requestPayload, result);
        return result;
    }

    private void markFriendInteraction(Long playerId, Long targetPlayerId) {
        Friend update = new Friend();
        update.setUpdatedAt(LocalDateTime.now());

        QueryWrapper<Friend> friendship = new QueryWrapper<>();
        friendship.eq("deleted", 0)
                .and(pair -> pair.eq("player_id", playerId).eq("friend_id", targetPlayerId)
                        .or().eq("player_id", targetPlayerId).eq("friend_id", playerId));
        friendMapper.update(update, friendship);
    }

    @Override
    public List<Map<String, Object>> getRecentInteractions(Long playerId) {
        Map<Long, Map<String, Object>> latestByPlayer = new LinkedHashMap<>();

        QueryWrapper<Friend> friendQuery = new QueryWrapper<>();
        friendQuery.eq("player_id", playerId)
                .eq("deleted", 0)
                .orderByDesc("updated_at")
                .last("LIMIT 20");
        for (Friend friend : friendMapper.selectList(friendQuery)) {
            Player other = playerMapper.selectById(friend.getFriendId());
            if (other == null) continue;
            mergeInteraction(latestByPlayer, other, "friend", "active",
                    friend.getUpdatedAt() != null ? friend.getUpdatedAt() : friend.getCreatedAt());
        }

        QueryWrapper<FriendRequest> requestQuery = new QueryWrapper<>();
        requestQuery.and(wrapper -> wrapper.eq("from_player_id", playerId).or().eq("to_player_id", playerId))
                .orderByDesc("updated_at")
                .last("LIMIT 20");
        for (FriendRequest request : friendRequestMapper.selectList(requestQuery)) {
            Long otherId = playerId.equals(request.getFromPlayerId())
                    ? request.getToPlayerId() : request.getFromPlayerId();
            Player other = playerMapper.selectById(otherId);
            if (other == null) continue;
            mergeInteraction(latestByPlayer, other, "friend_request", request.getStatus(),
                    request.getUpdatedAt() != null ? request.getUpdatedAt() : request.getCreatedAt());
        }

        List<Map<String, Object>> result = new ArrayList<>(latestByPlayer.values());
        result.sort(Comparator.comparing(
                entry -> String.valueOf(entry.getOrDefault("interactedAt", "")),
                Comparator.reverseOrder()));
        return result.size() > 20 ? new ArrayList<>(result.subList(0, 20)) : result;
    }

    private void mergeInteraction(Map<Long, Map<String, Object>> latestByPlayer,
                                  Player other,
                                  String interactionType,
                                  String status,
                                  LocalDateTime interactedAt) {
        String timestamp = interactedAt == null ? "" : interactedAt.toString();
        Map<String, Object> existing = latestByPlayer.get(other.getId());
        if (existing != null && String.valueOf(existing.getOrDefault("interactedAt", ""))
                .compareTo(timestamp) >= 0) {
            return;
        }
        Map<String, Object> stats = parseStatsJson(other.getStatsJson());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("playerId", other.getId());
        entry.put("playerName", other.getPlayerName());
        entry.put("level", toInt(stats.get("level"), 1));
        entry.put("avatarRank", stats.getOrDefault("avatarRank", "F"));
        entry.put("isOnline", onlinePlayerService.isOnline(other.getId()));
        entry.put("interactionType", interactionType);
        entry.put("status", status);
        entry.put("interactedAt", timestamp);
        latestByPlayer.put(other.getId(), entry);
    }

    // ─── internal ───

    private void createBidirectionalFriendship(Long playerId1, Long playerId2) {
        LocalDateTime now = LocalDateTime.now();

        // Check if already exists (re-add after remove)
        QueryWrapper<Friend> check1 = new QueryWrapper<>();
        check1.eq("player_id", playerId1).eq("friend_id", playerId2);
        Friend existing1 = friendMapper.selectOne(check1);

        if (existing1 != null) {
            existing1.setDeleted(0);
            existing1.setUpdatedAt(now);
            friendMapper.updateById(existing1);
        } else {
            Friend f1 = new Friend();
            f1.setPlayerId(playerId1);
            f1.setFriendId(playerId2);
            f1.setDeleted(0);
            f1.setCreatedAt(now);
            f1.setUpdatedAt(now);
            friendMapper.insert(f1);
        }

        QueryWrapper<Friend> check2 = new QueryWrapper<>();
        check2.eq("player_id", playerId2).eq("friend_id", playerId1);
        Friend existing2 = friendMapper.selectOne(check2);

        if (existing2 != null) {
            existing2.setDeleted(0);
            existing2.setUpdatedAt(now);
            friendMapper.updateById(existing2);
        } else {
            Friend f2 = new Friend();
            f2.setPlayerId(playerId2);
            f2.setFriendId(playerId1);
            f2.setDeleted(0);
            f2.setCreatedAt(now);
            f2.setUpdatedAt(now);
            friendMapper.insert(f2);
        }
    }

    private void writeLog(Long playerId, String type, String message) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        playerLogMapper.insert(log);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStatsJson(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
