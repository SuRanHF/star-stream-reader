package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.service.RankingService;
import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "排行榜系统", description = "全服排行榜、个人排名查询")
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @Operation(summary = "获取排行榜")
    @GetMapping
    public Result<List<RankingVO>> getRankings(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(rankingService.getRankings(limit));
    }

    @Operation(summary = "获取玩家个人排名")
    @GetMapping("/{playerId}")
    public Result<PlayerRankVO> getPlayerRank(@PathVariable Long playerId) {
        return Result.ok(rankingService.getPlayerRank(playerId));
    }
}
