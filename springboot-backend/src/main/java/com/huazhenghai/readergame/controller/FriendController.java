package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.HandleFriendRequestRequest;
import com.huazhenghai.readergame.dto.SendFriendRequestRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.FriendService;
import com.huazhenghai.readergame.vo.FriendRequestVO;
import com.huazhenghai.readergame.vo.FriendSummaryVO;
import com.huazhenghai.readergame.vo.FriendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friend", description = "好友系统")
public class FriendController {

    private final FriendService friendService;
    private final PlayerMapper playerMapper;

    public FriendController(FriendService friendService, PlayerMapper playerMapper) {
        this.friendService = friendService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/search")
    @Operation(summary = "搜索玩家")
    public Result<List<Map<String, Object>>> searchPlayers(
            @RequestParam String keyword,
            @RequestParam Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(friendService.searchPlayers(keyword, playerId));
    }

    @PostMapping("/request")
    @Operation(summary = "发送好友申请")
    public Result<Map<String, Object>> sendRequest(
            @RequestParam(required = false) Long playerId,
            @Valid @RequestBody SendFriendRequestRequest req) {
        Long pid = effectivePlayerId(req.getPlayerId(), playerId);
        validatePlayerOwnership(pid);
        return Result.ok(friendService.sendRequest(pid, req.getTargetPlayerId()));
    }

    @PostMapping("/accept")
    @Operation(summary = "接受好友申请")
    public Result<Map<String, Object>> acceptRequest(
            @RequestParam(required = false) Long playerId,
            @Valid @RequestBody HandleFriendRequestRequest req) {
        Long pid = effectivePlayerId(req.getPlayerId(), playerId);
        validatePlayerOwnership(pid);
        return Result.ok(friendService.acceptRequest(pid, req.getRequestId()));
    }

    @PostMapping("/reject")
    @Operation(summary = "拒绝好友申请")
    public Result<Map<String, Object>> rejectRequest(
            @RequestParam(required = false) Long playerId,
            @Valid @RequestBody HandleFriendRequestRequest req) {
        Long pid = effectivePlayerId(req.getPlayerId(), playerId);
        validatePlayerOwnership(pid);
        return Result.ok(friendService.rejectRequest(pid, req.getRequestId()));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消好友申请")
    public Result<Map<String, Object>> cancelRequest(
            @RequestParam(required = false) Long playerId,
            @Valid @RequestBody HandleFriendRequestRequest req) {
        Long pid = effectivePlayerId(req.getPlayerId(), playerId);
        validatePlayerOwnership(pid);
        return Result.ok(friendService.cancelRequest(pid, req.getRequestId()));
    }

    @GetMapping("/list")
    @Operation(summary = "获取好友列表")
    public Result<List<FriendVO>> getFriends(@RequestParam Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(friendService.getFriends(playerId));
    }

    @PostMapping("/remove")
    @Operation(summary = "删除好友")
    public Result<Map<String, Object>> removeFriend(
            @RequestParam(required = false) Long playerId,
            @Valid @RequestBody SendFriendRequestRequest req) {
        Long pid = effectivePlayerId(req.getPlayerId(), playerId);
        validatePlayerOwnership(pid);
        return Result.ok(friendService.removeFriend(pid, req.getTargetPlayerId()));
    }

    @GetMapping("/requests")
    @Operation(summary = "获取待处理的好友申请")
    public Result<List<FriendRequestVO>> getPendingRequests(@RequestParam Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(friendService.getPendingRequests(playerId));
    }

    @GetMapping("/summary")
    @Operation(summary = "获取好友摘要")
    public Result<FriendSummaryVO> getFriendSummary(@RequestParam Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(friendService.getFriendSummary(playerId));
    }

    @PostMapping("/gift")
    @Operation(summary = "赠送物品给好友")
    public Result<Map<String, Object>> sendGift(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long targetId = MapUtils.getLong(body, "targetId");
        String itemKey = MapUtils.getString(body, "itemKey");
        validatePlayerOwnership(playerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "礼物系统尚未完全实现: " + itemKey);
        return Result.ok(result);
    }

    @GetMapping("/recent/{playerId}")
    @Operation(summary = "获取最近互动玩家")
    public Result<List<Map<String, Object>>> getRecentInteractions(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId);
        return Result.ok(java.util.Collections.emptyList());
    }

    /** body playerId 优先, 否则用 query param (向后兼容) */
    private Long effectivePlayerId(Long bodyId, Long queryParamId) {
        return bodyId != null ? bodyId : queryParamId;
    }

    private void validatePlayerOwnership(Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }
}
