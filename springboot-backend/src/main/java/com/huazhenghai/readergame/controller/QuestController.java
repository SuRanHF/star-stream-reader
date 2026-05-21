package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.ClaimQuestRewardRequest;
import com.huazhenghai.readergame.dto.RefreshQuestRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quests")
@Tag(name = "Quest", description = "任务系统接口")
public class QuestController {

    private final QuestService questService;
    private final PlayerMapper playerMapper;

    public QuestController(QuestService questService, PlayerMapper playerMapper) {
        this.questService = questService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/{playerId}")
    @Operation(summary = "获取玩家任务列表")
    public Result<List<PlayerQuestVO>> getQuests(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        List<PlayerQuestVO> quests = questService.getAvailableQuests(playerId, userId);
        return Result.ok(quests);
    }

    @GetMapping("/summary/{playerId}")
    @Operation(summary = "获取玩家任务摘要")
    public Result<QuestSummaryVO> getSummary(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        questService.ensurePlayerQuests(playerId);
        QuestSummaryVO summary = questService.getQuestSummary(playerId);
        return Result.ok(summary);
    }

    @PostMapping("/claim")
    @Operation(summary = "领取任务奖励")
    public Result<QuestRewardVO> claimReward(@Valid @RequestBody ClaimQuestRewardRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        QuestRewardVO reward = questService.claimReward(
                req.getPlayerId(), req.getQuestKey(), req.getCycleKey(), userId);
        return Result.ok(reward);
    }

    @PostMapping("/refresh")
    @Operation(summary = "手动刷新任务列表")
    public Result<List<PlayerQuestVO>> refresh(@RequestBody RefreshQuestRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        questService.ensurePlayerQuests(req.getPlayerId());
        List<PlayerQuestVO> quests = questService.getAvailableQuests(req.getPlayerId(), userId);
        return Result.ok(quests);
    }

    @GetMapping("/definitions")
    @Operation(summary = "获取所有任务定义(公开)")
    public Result<List<QuestVO>> getDefinitions() {
        List<QuestVO> definitions = questService.getQuestDefinitions();
        return Result.ok(definitions);
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }
}
