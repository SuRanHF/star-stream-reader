package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.ChapterAdvanceRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.ChapterService;
import com.huazhenghai.readergame.vo.ChapterProgressVO;
import com.huazhenghai.readergame.vo.MainChapterVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chapters")
@Tag(name = "Chapter", description = "主线阶段系统接口")
public class ChapterController {

    private final ChapterService chapterService;
    private final PlayerMapper playerMapper;

    public ChapterController(ChapterService chapterService, PlayerMapper playerMapper) {
        this.chapterService = chapterService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/current/{playerId}")
    @Operation(summary = "获取玩家当前阶段进度")
    public Result<ChapterProgressVO> getCurrentChapter(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(chapterService.getCurrentChapter(playerId));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有主线阶段定义")
    public Result<List<MainChapterVO>> getAllChapters() {
        return Result.ok(chapterService.getAllMainChapters());
    }

    @GetMapping("/completed/{playerId}")
    @Operation(summary = "检查当前阶段是否已完成")
    public Result<Boolean> checkCompleted(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(chapterService.checkChapterCompleted(playerId));
    }

    @PostMapping("/advance")
    @Operation(summary = "领取奖励并推进阶段")
    public Result<ChapterProgressVO> advanceChapter(@Valid @RequestBody ChapterAdvanceRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(chapterService.claimChapterRewardAndAdvance(
                req.getPlayerId(), userId));
    }

    @GetMapping("/resources/{playerId}")
    @Operation(summary = "获取玩家突破资源")
    public Result<java.util.Map<String, Object>> getResources(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(chapterService.getResources(playerId));
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
