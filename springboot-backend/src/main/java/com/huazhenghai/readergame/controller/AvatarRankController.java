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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/avatar-rank")
@Tag(name = "AvatarRank", description = "化身位阶系统接口")
public class AvatarRankController {

    private final AvatarRankService avatarRankService;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public AvatarRankController(AvatarRankService avatarRankService,
                                 PlayerMapper playerMapper,
                                 ObjectMapper objectMapper) {
        this.avatarRankService = avatarRankService;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
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
    public Result<List<Map<String, Object>>> getLeaderboard() {
        return Result.ok(avatarRankService.getAvatarRankLeaderboard());
    }

    @PostMapping("/{playerId}/prestige")
    @Operation(summary = "回归(重置位阶到F，保留永久加成)")
    public Result<Map<String, Object>> prestige(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats;
        try {
            stats = objectMapper.readValue(player.getStatsJson(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            stats = new LinkedHashMap<>();
        }
        String currentRank = (String) stats.getOrDefault("avatarRank", "F");
        if (!"SSS".equals(currentRank))
            throw new BusinessException(ErrorCode.RANK_REQUIREMENTS_NOT_MET, "需要达到SSS位阶才能回归");

        // Calculate prestige level
        int prestigeLevel = Integer.parseInt(stats.getOrDefault("prestigeLevel", "0").toString()) + 1;
        int prestigeBonus = prestigeLevel * 5; // 5% bonus per prestige

        // Reset rank to F
        stats.put("avatarRank", "F");
        stats.put("avatarRankName", "临时化身");
        stats.put("prestigeLevel", prestigeLevel);
        stats.put("freePoints", 40 + prestigeLevel * 10);
        stats.put("level", 1);
        stats.put("exp", 0);
        stats.put("hp", 100);
        stats.put("maxHp", 100 + prestigeBonus);
        stats.put("attack", 10 + prestigeBonus);
        stats.put("defense", 5 + prestigeBonus / 2);
        stats.put("speed", 10 + prestigeBonus / 2);
        stats.put("critRate", 0.05 + prestigeLevel * 0.005);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新状态失败");
        }
        playerMapper.updateById(player);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "回归成功！你现在是 " + prestigeLevel + " 次回归者，获得永久加成 +" + prestigeBonus);
        result.put("prestigeLevel", prestigeLevel);
        result.put("bonus", prestigeBonus);
        return Result.ok(result);
    }
}
