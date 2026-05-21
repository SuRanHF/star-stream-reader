package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Friend;
import com.huazhenghai.readergame.entity.FriendRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.FriendMapper;
import com.huazhenghai.readergame.mapper.FriendRequestMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.FriendService;
import com.huazhenghai.readergame.service.OnlinePlayerService;
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

    public FriendServiceImpl(FriendMapper friendMapper,
                             FriendRequestMapper friendRequestMapper,
                             PlayerMapper playerMapper,
                             PlayerLogMapper playerLogMapper,
                             OnlinePlayerService onlinePlayerService,
                             ObjectMapper objectMapper) {
        this.friendMapper = friendMapper;
        this.friendRequestMapper = friendRequestMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.onlinePlayerService = onlinePlayerService;
        this.objectMapper = objectMapper;
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
        FriendRequest fr = friendRequestMapper.selectById(requestId);
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
    public Map<String, Object> rejectRequest(Long playerId, Long requestId) {
        FriendRequest fr = friendRequestMapper.selectById(requestId);
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
    public Map<String, Object> cancelRequest(Long playerId, Long requestId) {
        FriendRequest fr = friendRequestMapper.selectById(requestId);
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
