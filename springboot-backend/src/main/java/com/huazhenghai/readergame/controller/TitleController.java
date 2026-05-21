package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.TitleEquipRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.TitleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/titles")
@Tag(name = "Title", description = "称号系统接口")
public class TitleController {

    private final TitleService titleService;
    private final PlayerMapper playerMapper;

    public TitleController(TitleService titleService, PlayerMapper playerMapper) {
        this.titleService = titleService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有称号定义")
    public Result<List<TitleVO>> getAllTitles() {
        return Result.ok(titleService.getAllTitles());
    }

    @GetMapping("/my/{playerId}")
    @Operation(summary = "获取玩家拥有的称号")
    public Result<List<PlayerTitleVO>> getPlayerTitles(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(titleService.getPlayerTitles(playerId));
    }

    @GetMapping("/equipped/{playerId}")
    @Operation(summary = "获取玩家当前装备的称号")
    public Result<PlayerTitleVO> getEquippedTitle(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(titleService.getEquippedTitle(playerId));
    }

    @PostMapping("/evaluate/{playerId}")
    @Operation(summary = "评估并解锁新称号")
    public Result<List<PlayerTitleVO>> evaluateTitles(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId);
        return Result.ok(titleService.evaluateAndUnlockTitles(playerId, userId));
    }

    @PostMapping("/equip")
    @Operation(summary = "装备称号")
    public Result<PlayerTitleVO> equipTitle(@Valid @RequestBody TitleEquipRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(titleService.equipTitle(req.getPlayerId(), req.getTitleKey(), userId));
    }

    @GetMapping("/effects/{playerId}")
    @Operation(summary = "计算当前装备称号的效果加成")
    public Result<Map<String, Object>> calculateEffects(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(titleService.calculateTitleEffects(playerId));
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
