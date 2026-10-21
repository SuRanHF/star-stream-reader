package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.RankingService;
import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;
import com.huazhenghai.readergame.vo.StoryDiscoveryPositionVO;
import com.huazhenghai.readergame.vo.StoryDiscoveryRankingVO;
import com.huazhenghai.readergame.vo.PkPerformancePositionVO;
import com.huazhenghai.readergame.vo.PkPerformanceRankingVO;
import com.huazhenghai.readergame.vo.ExploreActionPositionVO;
import com.huazhenghai.readergame.vo.ExploreActionRankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@Tag(name = "排行榜系统", description = "全服排行榜、个人排名查询")
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;
    private final PlayerMapper playerMapper;

    public RankingController(RankingService rankingService, PlayerMapper playerMapper) {
        this.rankingService = rankingService;
        this.playerMapper = playerMapper;
    }

    @Operation(summary = "获取排行榜")
    @GetMapping
    public Result<List<RankingVO>> getRankings(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(rankingService.getRankings(limit));
    }

    @Operation(summary = "获取近七日首次完成故事种类排行")
    @GetMapping("/story-discoveries")
    public Result<List<StoryDiscoveryRankingVO>> getStoryDiscoveryRankings(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(rankingService.getStoryDiscoveryRankings(limit));
    }

    @Operation(summary = "获取本人近七日故事发现排行位置与追赶进度")
    @GetMapping("/story-discoveries/{playerId}")
    public Result<StoryDiscoveryPositionVO> getStoryDiscoveryPosition(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        return Result.ok(rankingService.getStoryDiscoveryPosition(playerId));
    }

    @Operation(summary = "获取近七日PK实际评分净变化排行")
    @GetMapping("/pk-performance")
    public Result<List<PkPerformanceRankingVO>> getPkPerformanceRankings(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(rankingService.getPkPerformanceRankings(limit));
    }

    @Operation(summary = "获取本人近七日PK表现与追赶差距")
    @GetMapping("/pk-performance/{playerId}")
    public Result<PkPerformancePositionVO> getPkPerformancePosition(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        return Result.ok(rankingService.getPkPerformancePosition(playerId));
    }

    @Operation(summary = "获取近七日探索行动次数排行")
    @GetMapping("/exploration-actions")
    public Result<List<ExploreActionRankingVO>> getExploreActionRankings(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(rankingService.getExploreActionRankings(limit));
    }

    @Operation(summary = "获取本人近七日探索行动次数与追赶差距")
    @GetMapping("/exploration-actions/{playerId}")
    public Result<ExploreActionPositionVO> getExploreActionPosition(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        return Result.ok(rankingService.getExploreActionPosition(playerId));
    }

    @Operation(summary = "获取玩家个人排名")
    @GetMapping("/{playerId}")
    public Result<PlayerRankVO> getPlayerRank(@PathVariable Long playerId) {
        return Result.ok(rankingService.getPlayerRank(playerId));
    }

    @Operation(summary = "获取玩家附近的定级对手")
    @GetMapping("/{playerId}/nearby")
    public Result<List<RankingVO>> getNearbyRankings(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "2") int radius) {
        return Result.ok(rankingService.getNearbyRankings(playerId, radius));
    }

    private void requireOwnedPlayer(Long playerId) {
        var loginUser = LoginUserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!Objects.equals(player.getUserId(), loginUser.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的排行进度");
        }
    }
}
