package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.AttackWorldBossRequest;
import com.huazhenghai.readergame.dto.ClaimWorldBossRewardRequest;
import com.huazhenghai.readergame.dto.CreateWorldBossRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/world-boss")
@Tag(name = "WorldBoss", description = "世界Boss讨伐系统")
public class WorldBossController {

    private final WorldBossService worldBossService;
    private final PlayerMapper playerMapper;

    public WorldBossController(WorldBossService worldBossService, PlayerMapper playerMapper) {
        this.worldBossService = worldBossService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/active")
    @Operation(summary = "获取当前active Boss")
    public Result<WorldBossVO> getActiveBoss(
            @RequestParam(required = false) Long playerId) {
        return Result.ok(worldBossService.getActiveBoss(playerId));
    }

    @GetMapping("/{bossNo}")
    @Operation(summary = "获取Boss详情")
    public Result<WorldBossVO> getBossDetail(
            @PathVariable String bossNo,
            @RequestParam(required = false) Long playerId) {
        return Result.ok(worldBossService.getBossDetail(bossNo, playerId));
    }

    @PostMapping("/attack")
    @Operation(summary = "攻击世界Boss")
    public Result<WorldBossAttackResultVO> attackBoss(
            @Valid @RequestBody AttackWorldBossRequest req) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        String bossNo = req.getBossNo();
        if (bossNo == null || bossNo.isBlank()) {
            // 前端兼容: 未传 bossNo 时自动取 active boss
            WorldBossVO active = worldBossService.getActiveBoss(req.getPlayerId());
            if (active == null) throw new BusinessException(ErrorCode.NOT_FOUND, "当前没有活跃的世界Boss");
            bossNo = active.getBossNo();
        }
        return Result.ok(worldBossService.attackBoss(req.getPlayerId(), bossNo, user.getUserId()));
    }

    @GetMapping("/rankings/{bossNo}")
    @Operation(summary = "Boss伤害排行榜")
    public Result<List<WorldBossRankingVO>> getBossRankings(
            @PathVariable String bossNo) {
        return Result.ok(worldBossService.getBossRankings(bossNo));
    }

    @PostMapping("/claim")
    @Operation(summary = "领取Boss奖励")
    public Result<WorldBossRewardVO> claimReward(
            @Valid @RequestBody ClaimWorldBossRewardRequest req) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        return Result.ok(worldBossService.claimReward(req.getPlayerId(), req.getBossNo(), user.getUserId()));
    }

    @GetMapping("/my/{playerId}")
    @Operation(summary = "我的参与记录")
    public Result<List<WorldBossParticipationVO>> getMyParticipation(
            @PathVariable Long playerId) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        return Result.ok(worldBossService.getMyParticipation(playerId, user.getUserId()));
    }

    @GetMapping("/summary/{playerId}")
    @Operation(summary = "世界Boss摘要")
    public Result<WorldBossSummaryVO> getSummary(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(worldBossService.getWorldBossSummary(playerId));
    }

    @GetMapping("/history")
    @Operation(summary = "世界Boss历史")
    public Result<List<Map<String, Object>>> getHistory() {
        return Result.ok(java.util.Collections.emptyList());
    }

    // ─── Admin endpoints ───

    @PostMapping("/admin/create")
    @Operation(summary = "Admin创建Boss")
    public Result<WorldBossVO> createBoss(@Valid @RequestBody CreateWorldBossRequest req) {
        LoginUser user = assertAdmin();
        return Result.ok(worldBossService.createBoss(req, user.getUserId()));
    }

    @PostMapping("/admin/open")
    @Operation(summary = "Admin手动开启Boss")
    public Result<WorldBossVO> openBoss() {
        assertAdmin();
        return Result.ok(worldBossService.openScheduledBoss());
    }

    @PostMapping("/admin/settle")
    @Operation(summary = "Admin手动结算Boss")
    public Result<String> settleBoss(@RequestParam String bossNo) {
        assertAdmin();
        worldBossService.settleBoss(bossNo);
        return Result.ok("结算完成");
    }

    private void validatePlayerOwnership(Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    private LoginUser assertAdmin() {
        LoginUser user = LoginUserContext.get();
        if (user == null || !"admin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可操作");
        }
        return user;
    }
}
