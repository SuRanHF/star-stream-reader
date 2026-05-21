package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.NarrativeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/narrative")
public class NarrativeController {

    private final NarrativeService narrativeService;
    private final PlayerMapper playerMapper;

    public NarrativeController(NarrativeService narrativeService, PlayerMapper playerMapper) {
        this.narrativeService = narrativeService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/item-memories/{itemKey}")
    public Result<List<Map<String, Object>>> getItemMemories(@PathVariable String itemKey) {
        return Result.ok(narrativeService.getItemMemories(itemKey));
    }

    @GetMapping("/item-memories")
    public Result<List<Map<String, Object>>> getAllItemMemories() {
        return Result.ok(narrativeService.getAllItemMemories());
    }

    @GetMapping("/location-echoes/{locationKey}")
    public Result<List<Map<String, Object>>> getLocationEchoes(@PathVariable String locationKey) {
        return Result.ok(narrativeService.getLocationEchoes(locationKey));
    }

    @GetMapping("/ghost-check/{playerId}/{locationKey}")
    public Result<Map<String, Object>> checkNpcGhost(@PathVariable Long playerId, @PathVariable String locationKey) {
        validatePlayerOwnership(playerId);
        Map<String, Object> ghost = narrativeService.checkNpcGhostEncounter(playerId, locationKey);
        return Result.ok(ghost);
    }

    @PostMapping("/ghost-encounter")
    public Result<Map<String, Object>> processEncounter(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        validatePlayerOwnership(playerId);
        String ghostKey = MapUtils.getStringRequired(body, "ghostKey");
        int nodeIndex = MapUtils.getIntOrDefault(body, "nodeIndex", 0);
        int choiceIndex = MapUtils.getIntOrDefault(body, "choiceIndex", 0);

        Map<String, Object> result = narrativeService.processNpcGhostEncounter(playerId, ghostKey, nodeIndex, choiceIndex);
        if (result.containsKey("error")) {
            return Result.fail("NARRATIVE_ERROR", String.valueOf(result.get("error")));
        }
        return Result.ok(result);
    }

    @GetMapping("/encounters/{playerId}")
    public Result<List<Map<String, Object>>> getEncounters(@PathVariable Long playerId,
                                                            @RequestParam(defaultValue = "20") int limit) {
        validatePlayerOwnership(playerId);
        return Result.ok(narrativeService.getPlayerEncounters(playerId, limit));
    }

    private void validatePlayerOwnership(Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return def; }
    }
}
