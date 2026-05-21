package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.StartCombatRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.vo.BattleLogVO;
import com.huazhenghai.readergame.vo.CombatResultVO;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import com.huazhenghai.readergame.vo.MonsterVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "战斗系统", description = "怪物查询、战斗结算、战斗日志")
@RestController
@RequestMapping("/api/combat")
public class CombatController {

    private final CombatService combatService;
    private final PlayerMapper playerMapper;

    public CombatController(CombatService combatService, PlayerMapper playerMapper) {
        this.combatService = combatService;
        this.playerMapper = playerMapper;
    }

    @Operation(summary = "获取所有已启用怪物")
    @GetMapping("/monsters")
    public Result<List<MonsterVO>> getAllMonsters() {
        return Result.ok(combatService.getAllMonsters());
    }

    @Operation(summary = "按地点获取怪物列表")
    @GetMapping("/monsters/by-location/{locationKey}")
    public Result<List<MonsterVO>> getMonstersByLocation(@PathVariable String locationKey) {
        return Result.ok(combatService.getMonstersByLocation(locationKey));
    }

    @Operation(summary = "开始战斗")
    @PostMapping("/start")
    public Result<CombatResultVO> startCombat(@Valid @RequestBody StartCombatRequest request) {
        Long userId = LoginUserContext.get().getUserId();
        CombatResultVO result = combatService.startCombat(
                request.getPlayerId(), request.getMonsterKey(), userId);
        return Result.ok(result);
    }

    @Operation(summary = "战斗操作 (攻击/逃跑等)")
    @PostMapping("/resolve")
    public Result<Map<String, Object>> resolveCombat(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        String monsterKey = MapUtils.getString(body, "monsterKey");
        String action = MapUtils.getString(body, "action");
        if (action == null) action = "attack";
        Map<String, Object> result = combatService.resolveCombat(playerId, monsterKey, action, userId);
        return Result.ok(result);
    }

    @Operation(summary = "获取玩家战斗日志")
    @GetMapping("/logs/{playerId}")
    public Result<List<BattleLogVO>> getBattleLogs(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(combatService.getBattleLogs(playerId, userId));
    }

    @Operation(summary = "查看玩家战斗属性")
    @GetMapping("/stats/{playerId}")
    public Result<CombatStatsVO> getCombatPower(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null || !player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此玩家");
        return Result.ok(combatService.calculateCombatPower(playerId));
    }
}
