package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.UnlockSkillRequest;
import com.huazhenghai.readergame.dto.LearnFactionSkillRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.service.FactionService;
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
    private final FactionService factionService;
    private final PlayerMapper playerMapper;

    public SkillController(SkillService skillService, FactionService factionService, PlayerMapper playerMapper) {
        this.skillService = skillService;
        this.factionService = factionService;
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
        PlayerSkillVO result = skillService.unlockSkill(
                req.getPlayerId(), req.getSkillKey(), userId, req.getRequestKey());
        return Result.ok(result);
    }

    @PostMapping("/equip")
    @Operation(summary = "装备技能")
    public Result<PlayerSkillVO> equipSkill(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = Long.valueOf(body.get("playerId").toString());
        String skillKey = (String) body.get("skillKey");
        PlayerSkillVO result = skillService.equipSkill(playerId, skillKey, userId);
        return Result.ok(result);
    }

    @PostMapping("/unequip")
    @Operation(summary = "卸下技能")
    public Result<PlayerSkillVO> unequipSkill(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = Long.valueOf(body.get("playerId").toString());
        String skillKey = (String) body.get("skillKey");
        PlayerSkillVO result = skillService.unequipSkill(playerId, skillKey, userId);
        return Result.ok(result);
    }

    @GetMapping("/faction/{constellationKey}")
    @Operation(summary = "阵营技能列表")
    public Result<List<FactionSkillVO>> getFactionSkills(@PathVariable String constellationKey) {
        return Result.ok(factionService.getFactionSkills(constellationKey));
    }

    @GetMapping("/faction/player/{playerId}")
    @Operation(summary = "玩家阵营技能")
    public Result<List<FactionSkillVO>> getPlayerFactionSkills(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(factionService.getPlayerFactionSkills(playerId, userId));
    }

    @PostMapping("/faction/learn")
    @Operation(summary = "学习阵营技能")
    public Result<FactionSkillVO> learnFactionSkill(@Valid @RequestBody LearnFactionSkillRequest request) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(factionService.learnFactionSkill(
                request.getPlayerId(), request.getSkillKey(), userId, request.getRequestKey()));
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
