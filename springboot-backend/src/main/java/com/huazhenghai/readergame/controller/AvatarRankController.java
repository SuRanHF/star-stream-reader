package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.RankUpRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.vo.AvatarRankVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/avatar-rank")
@Tag(name = "AvatarRank", description = "化身位阶系统接口")
public class AvatarRankController {

    private final AvatarRankService avatarRankService;
    private final PlayerMapper playerMapper;

    public AvatarRankController(AvatarRankService avatarRankService,
                                 PlayerMapper playerMapper) {
        this.avatarRankService = avatarRankService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/{playerId}")
    @Operation(summary = "获取玩家位阶详情和升阶进度")
    public Result<AvatarRankVO> getAvatarRankInfo(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null || !player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此玩家");
        return Result.ok(avatarRankService.getAvatarRankInfo(playerId));
    }

    @PostMapping("/{playerId}/rank-up")
    @Operation(summary = "尝试升阶")
    public Result<AvatarRankVO> rankUp(@PathVariable Long playerId,
                                        @Valid @RequestBody RankUpRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(avatarRankService.rankUp(playerId, userId));
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "获取位阶排行榜")
    public Result<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(defaultValue = "200") int limit) {
        return Result.ok(avatarRankService.getAvatarRankLeaderboard(limit));
    }

    @PostMapping("/{playerId}/prestige")
    @Operation(summary = "回归(重置位阶到F，保留永久加成)")
    public Result<Map<String, Object>> prestige(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(avatarRankService.prestige(playerId, userId));
    }
}
