package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.HelpBountyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bounty")
public class HelpBountyController {

    private final HelpBountyService bountyService;
    private final PlayerMapper playerMapper;

    public HelpBountyController(HelpBountyService bountyService, PlayerMapper playerMapper) {
        this.bountyService = bountyService;
        this.playerMapper = playerMapper;
    }

    @PostMapping("/publish")
    public Result<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        String monsterKey = MapUtils.getString(body, "monsterKey");
        if (monsterKey == null) monsterKey = "";
        String locationKey = MapUtils.getString(body, "locationKey");
        if (locationKey == null) locationKey = "";
        String monsterName = MapUtils.getString(body, "monsterName");
        if (monsterName == null) monsterName = "";
        int sharePercent = MapUtils.getIntOrDefault(body, "sharePercent", 50);
        @SuppressWarnings("unchecked")
        Map<String, Object> combatRewards = (Map<String, Object>) body.getOrDefault("combatRewards", Map.of());

        Map<String, Object> result = bountyService.publishBounty(playerId, monsterKey, locationKey, monsterName, sharePercent, combatRewards);
        if (result.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) result.get("error");
            return Result.fail(String.valueOf(err.get("code")), String.valueOf(err.get("message")));
        }
        return Result.ok(result);
    }

    @PostMapping("/accept/{bountyId}")
    public Result<Map<String, Object>> accept(@PathVariable Long bountyId, @RequestBody Map<String, Object> body) {
        Long helperId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(helperId, userId);
        Map<String, Object> result = bountyService.acceptBounty(bountyId, helperId);
        if (result.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) result.get("error");
            return Result.fail(String.valueOf(err.get("code")), String.valueOf(err.get("message")));
        }
        return Result.ok(result);
    }

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending() {
        return Result.ok(bountyService.getPendingBounties());
    }

    @GetMapping("/my/{playerId}")
    public Result<Map<String, Object>> myBounty(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        return Result.ok(bountyService.getMyActiveBounty(playerId));
    }

    @PostMapping("/cancel")
    public Result<Map<String, Object>> cancel(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        Map<String, Object> result = bountyService.cancelBounty(playerId);
        if (result.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> err = (Map<String, Object>) result.get("error");
            return Result.fail(String.valueOf(err.get("code")), String.valueOf(err.get("message")));
        }
        return Result.ok(result);
    }

    @GetMapping("/daily-limits/{playerId}")
    public Result<Map<String, Object>> dailyLimits(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        return Result.ok(bountyService.getDailyCounts(playerId));
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
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
