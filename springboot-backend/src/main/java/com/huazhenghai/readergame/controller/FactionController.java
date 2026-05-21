package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.ContributeFactionRequest;
import com.huazhenghai.readergame.dto.JoinFactionRequest;
import com.huazhenghai.readergame.dto.LeaveFactionRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/factions")
@Tag(name = "Faction", description = "星座阵营系统")
public class FactionController {

    private final FactionService factionService;
    private final PlayerMapper playerMapper;

    public FactionController(FactionService factionService, PlayerMapper playerMapper) {
        this.factionService = factionService;
        this.playerMapper = playerMapper;
    }

    @GetMapping
    @Operation(summary = "获取所有阵营")
    public Result<List<FactionVO>> getAllFactions(
            @RequestParam(required = false) Long playerId) {
        return Result.ok(factionService.getAllFactions(playerId));
    }

    @GetMapping("/{factionKey}")
    @Operation(summary = "获取阵营详情")
    public Result<FactionVO> getFactionDetail(
            @PathVariable String factionKey,
            @RequestParam(required = false) Long playerId) {
        return Result.ok(factionService.getFactionDetail(factionKey, playerId));
    }

    @GetMapping("/my/{playerId}")
    @Operation(summary = "获取我的阵营")
    public Result<PlayerFactionVO> getMyFaction(@PathVariable Long playerId) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        return Result.ok(factionService.getMyFaction(playerId, user.getUserId()));
    }

    @PostMapping("/join")
    @Operation(summary = "加入阵营")
    public Result<PlayerFactionVO> joinFaction(@Valid @RequestBody JoinFactionRequest req) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        return Result.ok(factionService.joinFaction(req.getPlayerId(), req.getFactionKey(), user.getUserId()));
    }

    @PostMapping("/leave")
    @Operation(summary = "退出阵营")
    public Result<String> leaveFaction(@Valid @RequestBody LeaveFactionRequest req) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        factionService.leaveFaction(req.getPlayerId(), user.getUserId());
        return Result.ok("已退出阵营");
    }

    @PostMapping("/contribute")
    @Operation(summary = "贡献阵营")
    public Result<FactionContributionVO> contribute(@Valid @RequestBody ContributeFactionRequest req) {
        LoginUser user = LoginUserContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        return Result.ok(factionService.contribute(
                req.getPlayerId(), req.getFactionKey(), req.getContributionType(),
                req.getValue(), req.getSource(), req.getRelatedId(), user.getUserId()));
    }

    @GetMapping("/rankings")
    @Operation(summary = "阵营排行榜")
    public Result<List<FactionRankingVO>> getFactionRankings() {
        return Result.ok(factionService.getFactionRankings());
    }

    @GetMapping("/{factionKey}/contribution-rankings")
    @Operation(summary = "阵营内贡献排行")
    public Result<List<FactionRankingVO>> getFactionContributionRankings(
            @PathVariable String factionKey) {
        return Result.ok(factionService.getFactionContributionRankings(factionKey));
    }

    @GetMapping("/{factionKey}/skills")
    @Operation(summary = "阵营技能定义")
    public Result<List<FactionSkillVO>> getFactionSkills(@PathVariable String factionKey) {
        return Result.ok(factionService.getFactionSkills(factionKey));
    }

    @GetMapping("/wars")
    @Operation(summary = "阵营战列表")
    public Result<List<FactionWarVO>> getFactionWars() {
        return Result.ok(factionService.getFactionWars());
    }

    @GetMapping("/{factionKey}/members")
    @Operation(summary = "阵营成员列表")
    public Result<java.util.List<java.util.Map<String, Object>>> getFactionMembers(
            @PathVariable String factionKey,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(factionService.getFactionMembers(factionKey, limit));
    }

    @GetMapping("/summary/{playerId}")
    @Operation(summary = "阵营摘要")
    public Result<FactionSummaryVO> getFactionSummary(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(factionService.getFactionSummary(playerId));
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
