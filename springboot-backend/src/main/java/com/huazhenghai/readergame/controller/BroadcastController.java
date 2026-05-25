package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.ClaimBroadcastRewardRequest;
import com.huazhenghai.readergame.dto.ContributeBroadcastRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.vo.BroadcastEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/broadcast")
@Tag(name = "Broadcast", description = "星流放送广播系统")
public class BroadcastController {

    private final BroadcastService broadcastService;
    private final PlayerMapper playerMapper;

    public BroadcastController(BroadcastService broadcastService, PlayerMapper playerMapper) {
        this.broadcastService = broadcastService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/active")
    @Operation(summary = "获取活跃广播列表")
    public Result<List<BroadcastEventVO>> getActiveBroadcasts() {
        return Result.ok(broadcastService.getActiveBroadcasts());
    }

    @GetMapping("/{eventKey}")
    @Operation(summary = "获取广播详情")
    public Result<BroadcastEventVO> getBroadcastDetail(@PathVariable String eventKey) {
        return Result.ok(broadcastService.getBroadcastDetail(eventKey));
    }

    @PostMapping("/contribute")
    @Operation(summary = "贡献进度")
    public Result<Map<String, Object>> contribute(@Valid @RequestBody ContributeBroadcastRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getPlayerId(), userId);
        Map<String, Object> result = broadcastService.contribute(
                req.getEventKey(), req.getPlayerId(),
                req.getValue() != null ? req.getValue() : 1,
                req.getContributionType());
        return Result.ok(result);
    }

    @PostMapping("/claim")
    @Operation(summary = "领取奖励")
    public Result<Map<String, Object>> claimReward(@Valid @RequestBody ClaimBroadcastRewardRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getPlayerId(), userId);
        Map<String, Object> result = broadcastService.claimReward(req.getEventKey(), req.getPlayerId());
        return Result.ok(result);
    }

    @GetMapping("/rankings")
    @Operation(summary = "星流贡献榜")
    public Result<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(broadcastService.getLeaderboard(limit));
    }

    @GetMapping("/my/{playerId}")
    @Operation(summary = "查看玩家贡献记录")
    public Result<List<Map<String, Object>>> getPlayerContributions(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        return Result.ok(broadcastService.getPlayerContributions(playerId));
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }
}
