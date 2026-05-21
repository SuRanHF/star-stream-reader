package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.ChapterService;
import com.huazhenghai.readergame.service.PlayerService;
import com.huazhenghai.readergame.vo.ChapterProgressVO;
import com.huazhenghai.readergame.vo.PlayerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 故事控制器 - 兼容前端 /api/story/** 旧路径.
 */
@RestController
@RequestMapping("/api/story")
@Tag(name = "Story", description = "故事/剧情接口 (兼容前端)")
public class StoryController {

    private final PlayerService playerService;
    private final ChapterService chapterService;
    private final PlayerMapper playerMapper;

    public StoryController(PlayerService playerService, ChapterService chapterService,
                           PlayerMapper playerMapper) {
        this.playerService = playerService;
        this.chapterService = chapterService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/current/{playerId}")
    @Operation(summary = "获取当前剧情状态")
    public Result<Map<String, Object>> getCurrentStory(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        Player player = playerService.getPlayerEntity(playerId);
        PlayerVO playerVO = playerService.getPlayer(playerId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("player", playerVO);
        result.put("chapter", null);
        result.put("choices", Collections.emptyList());
        result.put("locked_choices", Collections.emptyList());
        result.put("chapter_consumed", false);

        if (player != null && player.getCurrentMainChapter() != null) {
            try {
                ChapterProgressVO progress = chapterService.getCurrentChapter(playerId);
                if (progress != null) {
                    Map<String, Object> chapter = new LinkedHashMap<>();
                    chapter.put("chapter_key", progress.getCurrentChapterKey());
                    chapter.put("chapter_name", progress.getCurrentChapterName());
                    chapter.put("summary", "");
                    chapter.put("content", "");
                    result.put("chapter", chapter);
                    result.put("chapter_consumed", Boolean.TRUE.equals(progress.isCompleted()));
                }
            } catch (Exception e) {
                // Chapter not available yet
            }
        }

        return Result.ok(result);
    }

    @PostMapping("/choose")
    @Operation(summary = "做出剧情选择")
    public Result<Map<String, Object>> makeChoice(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        validatePlayerOwnership(playerId);

        PlayerVO playerVO = playerService.getPlayer(playerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("player", playerVO);
        result.put("new_logs", Collections.emptyList());
        result.put("chapter_consumed", false);

        return Result.ok(result);
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
