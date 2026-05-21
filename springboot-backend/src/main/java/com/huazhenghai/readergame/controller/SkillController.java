package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.UnlockSkillRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@Tag(name = "Skill", description = "技能系统接口")
public class SkillController {

    private final SkillService skillService;
    private final PlayerMapper playerMapper;

    public SkillController(SkillService skillService, PlayerMapper playerMapper) {
        this.skillService = skillService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部技能定义")
    public Result<List<SkillVO>> getAllSkills() {
        return Result.ok(skillService.getAllSkills());
    }

    @GetMapping("/{playerId}")
    @Operation(summary = "获取玩家已解锁技能")
    public Result<List<PlayerSkillVO>> getPlayerSkills(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(skillService.getPlayerSkills(playerId, userId));
    }

    @GetMapping("/unlockable/{playerId}")
    @Operation(summary = "获取当前可解锁技能")
    public Result<List<SkillVO>> getUnlockableSkills(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(skillService.evaluateUnlockableSkills(playerId));
    }

    @GetMapping("/bonus/{playerId}")
    @Operation(summary = "获取技能属性加成")
    public Result<Map<String, Object>> getSkillBonus(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        skillService.getPlayerSkills(playerId, userId);
        return Result.ok(skillService.calculateSkillBonus(playerId));
    }

    @PostMapping("/unlock")
    @Operation(summary = "解锁技能")
    public Result<PlayerSkillVO> unlockSkill(@Valid @RequestBody UnlockSkillRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        PlayerSkillVO result = skillService.unlockSkill(req.getPlayerId(), req.getSkillKey(), userId);
        return Result.ok(result);
    }

    // 阵营技能占位 — 返回 NOT_MIGRATED
    @GetMapping("/faction/{constellationKey}")
    @Operation(summary = "阵营技能列表 (暂未迁移)")
    public Result<Void> getFactionSkills(@PathVariable String constellationKey) {
        return Result.fail("NOT_MIGRATED", "阵营技能尚未迁移");
    }

    @GetMapping("/faction/player/{playerId}")
    @Operation(summary = "玩家阵营技能 (暂未迁移)")
    public Result<Void> getPlayerFactionSkills(@PathVariable Long playerId) {
        return Result.fail("NOT_MIGRATED", "阵营技能尚未迁移");
    }

    @PostMapping("/faction/learn")
    @Operation(summary = "学习阵营技能 (暂未迁移)")
    public Result<Void> learnFactionSkill(@RequestBody Map<String, Object> body) {
        return Result.fail("NOT_MIGRATED", "阵营技能尚未迁移");
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
