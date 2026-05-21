package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.CreatePlayerRequest;
import com.huazhenghai.readergame.dto.RestRequest;
import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.vo.RestStateVO;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.PlayerService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家控制器.
 * <p>
 * 负责创建角色、获取角色信息、休息管理.
 * 所有接口均需要登录 (由 JwtAuthInterceptor 校验).
 * </p>
 */
@RestController
@RequestMapping("/api/player")
@Tag(name = "Player", description = "玩家角色接口")
public class PlayerController {

    private final PlayerService playerService;
    private final RecoveryService recoveryService;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public PlayerController(PlayerService playerService,
                            RecoveryService recoveryService,
                            PlayerMapper playerMapper,
                            ObjectMapper objectMapper) {
        this.playerService = playerService;
        this.recoveryService = recoveryService;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建玩家角色.
     * <p>
     * 每个用户只能创建一个角色.
     * </p>
     */
    @PostMapping("/create")
    @Operation(summary = "创建玩家角色")
    public Result<PlayerVO> createPlayer(@Valid @RequestBody CreatePlayerRequest req) {
        LoginUser loginUser = LoginUserContext.get();
        PlayerVO player = playerService.create(req.getPlayerName(), loginUser.getUserId());
        return Result.ok(player);
    }

    /**
     * 获取当前登录用户的玩家角色.
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户的玩家角色")
    public Result<PlayerVO> getMyPlayer() {
        LoginUser loginUser = LoginUserContext.get();
        Player player = playerService.findByUserId(loginUser.getUserId());
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "尚未创建角色");
        }
        PlayerVO vo = playerService.getPlayer(player.getId());
        return Result.ok(vo);
    }

    /**
     * 根据玩家 ID 获取玩家详情.
     * <p>
     * 需要校验所有权: 玩家必须属于当前登录用户.
     * 管理员例外 (后续扩展).
     * </p>
     */
    @GetMapping("/{playerId}")
    @Operation(summary = "获取指定玩家详情 (需本人)")
    public Result<PlayerVO> getPlayerById(@PathVariable Long playerId) {
        LoginUser loginUser = LoginUserContext.get();

        // 获取玩家实体并校验所有权
        Player player = playerService.getPlayerEntity(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!loginUser.getUserId().equals(player.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此玩家数据");
        }

        PlayerVO vo = playerService.getPlayer(playerId);
        return Result.ok(vo);
    }

    // ─── 休息管理 ───

    @PostMapping("/rest/start")
    @Operation(summary = "开始休息")
    public Result<Map<String, Object>> startRest(@Valid @RequestBody RestRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> stats = recoveryService.startRest(req.getPlayerId(), userId);
        return Result.ok(stats);
    }

    @PostMapping("/rest/stop")
    @Operation(summary = "结束休息")
    public Result<Map<String, Object>> stopRest(@Valid @RequestBody RestRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> stats = recoveryService.stopRest(req.getPlayerId(), userId);
        return Result.ok(stats);
    }

    @GetMapping("/rest/state")
    @Operation(summary = "获取休息状态")
    public Result<RestStateVO> getRestState(@RequestParam Long playerId) {
        Long userId = LoginUserContext.get().getUserId();

        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = recoveryService.applyRecovery(player);

        RestStateVO vo = new RestStateVO();
        vo.setIsResting(Boolean.TRUE.equals(stats.get("isResting")));
        Object restStartedAt = stats.get("restStartedAt");
        vo.setRestStartedAt(restStartedAt != null ? restStartedAt.toString() : null);
        vo.setHp(toInt(stats.get("hp"), 100));
        vo.setMaxHp(toInt(stats.get("maxHp"), 100));
        vo.setStamina(toInt(stats.get("stamina"), 50));
        vo.setMaxStamina(toInt(stats.get("maxStamina"), 50));
        Object lastRecoveryAt = stats.get("lastRecoveryAt");
        vo.setLastRecoveryAt(lastRecoveryAt != null ? lastRecoveryAt.toString() : null);

        return Result.ok(vo);
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    // ─── 星座 (背后星) ───

    @GetMapping("/constellations")
    @Operation(summary = "获取所有星座/背后星")
    public Result<Map<String, Object>> getConstellations() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        String[][] data = {
            {"governing_surveillance", "支配之眼", "监视与掌控的星座"},
            {"abyssal_flame", "深渊黑焰", "毁灭与重生的星座"},
            {"golden_roulette", "黄金轮盘", "命运与赌博的星座"},
            {"monarchs_whisper", "君王低语", "统治与征服的星座"},
            {"celestial_plow", "天犁", "耕耘与收获的星座"},
            {"crimson_wisdom", "赤红智慧", "知识与诡计的星座"},
            {"silent_veil", "沉默面纱", "隐秘与庇护的星座"},
            {"eternal_prison", "永恒囚笼", "束缚与秩序的星座"},
        };
        for (String[] d : data) {
            java.util.Map<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("key", d[0]);
            c.put("name", d[1]);
            c.put("title", d[1]);
            c.put("description", d[2]);
            c.put("emoji", "⭐");
            list.add(c);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("constellations", list);
        return Result.ok(result);
    }

    @PostMapping("/select-constellation")
    @Operation(summary = "选择背后星")
    public Result<Map<String, Object>> selectConstellation(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        String constellationKey = MapUtils.getStringRequired(body, "constellationKey");
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        stats.put("constellation", constellationKey);
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新星座失败");
        }
        playerMapper.updateById(player);
        java.util.Map<String, Object> constellation = new java.util.LinkedHashMap<>();
        String[][] data = {
            {"governing_surveillance", "支配之眼", "监视与掌控的星座"},
            {"abyssal_flame", "深渊黑焰", "毁灭与重生的星座"},
            {"golden_roulette", "黄金轮盘", "命运与赌博的星座"},
            {"monarchs_whisper", "君王低语", "统治与征服的星座"},
            {"celestial_plow", "天犁", "耕耘与收获的星座"},
            {"crimson_wisdom", "赤红智慧", "知识与诡计的星座"},
            {"silent_veil", "沉默面纱", "隐秘与庇护的星座"},
            {"eternal_prison", "永恒囚笼", "束缚与秩序的星座"},
        };
        for (String[] d : data) {
            if (d[0].equals(constellationKey)) {
                constellation.put("key", d[0]);
                constellation.put("name", d[1]);
                constellation.put("title", d[1]);
                break;
            }
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("constellation", constellation);
        return Result.ok(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    // ─── 冥界 / 死亡 / 复活 ───

    @GetMapping("/dead-list")
    @Operation(summary = "获取所有死亡玩家列表（公开）")
    public Result<java.util.List<Map<String, Object>>> getDeadPlayers() {
        java.util.List<Map<String, Object>> deadList = new java.util.ArrayList<>();
        java.util.List<Player> allPlayers = playerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Player>()
                        .last("LIMIT 500"));
        for (Player p : allPlayers) {
            Map<String, Object> stats = parseJsonMap(p.getStatsJson());
            if (Boolean.TRUE.equals(stats.get("isDead"))) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", p.getId());
                entry.put("player_name", p.getPlayerName());
                entry.put("level", stats.getOrDefault("level", 1));
                entry.put("constellation", stats.getOrDefault("constellation", ""));
                deadList.add(entry);
            }
        }
        return Result.ok(deadList);
    }

    @PostMapping("/revive")
    @Operation(summary = "自我复活")
    public Result<Map<String, Object>> revivePlayer(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        String method = MapUtils.getString(body, "method");
        if (method == null) method = "coins";
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        if (!Boolean.TRUE.equals(stats.get("isDead"))) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "玩家未死亡");
        }

        if ("coins".equals(method)) {
            int level = toInt(stats.get("level"), 1);
            int cost = 100 * level;
            if (player.getCoins() < cost) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "金币不足，需要 " + cost + " 金币");
            }
            player.setCoins(player.getCoins() - cost);
        }
        // title sacrifice method: just revive (title is handled by frontend)

        stats.put("isDead", false);
        int maxHp = toInt(stats.get("maxHp"), 100);
        stats.put("hp", maxHp / 2); // revive with half HP
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新状态失败");
        }
        playerMapper.updateById(player);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "你从冥界归来了。HP已恢复至" + (maxHp / 2) + "点，请休息恢复更多生命值。");
        return Result.ok(result);
    }

    @PostMapping("/peer-revive")
    @Operation(summary = "复活其他玩家")
    public Result<Map<String, Object>> peerRevive(@RequestBody Map<String, Object> body) {
        Long reviverId = MapUtils.getLong(body, "reviverId");
        Long targetId = MapUtils.getLong(body, "targetId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(reviverId, userId);
        String method = (String) body.getOrDefault("method", "coins");

        Player reviver = playerMapper.selectById(reviverId);
        Player target = playerMapper.selectById(targetId);
        if (target == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "目标玩家不存在");

        Map<String, Object> targetStats = parseJsonMap(target.getStatsJson());
        if (!Boolean.TRUE.equals(targetStats.get("isDead"))) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "目标玩家未死亡");
        }

        if ("coins".equals(method)) {
            int level = toInt(targetStats.get("level"), 1);
            String constellation = (String) targetStats.getOrDefault("constellation", "");
            double multiplier = "queen_of_underworld".equals(constellation) ? 0.5 : 1.0;
            int cost = (int) Math.round(100 * level * multiplier);
            if (reviver.getCoins() < cost) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "金币不足，需要 " + cost + " 金币");
            }
            reviver.setCoins(reviver.getCoins() - cost);
            playerMapper.updateById(reviver);
            targetStats.put("isDead", false);
            int maxHp = toInt(targetStats.get("maxHp"), 100);
            targetStats.put("hp", maxHp / 2);
        } else {
            // title sacrifice: just revive
            targetStats.put("isDead", false);
            int maxHp = toInt(targetStats.get("maxHp"), 100);
            targetStats.put("hp", maxHp / 2);
        }

        try {
            target.setStatsJson(objectMapper.writeValueAsString(targetStats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新状态失败");
        }
        playerMapper.updateById(target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "你从冥界拉回了一位玩家。");
        return Result.ok(result);
    }

    // ─── 属性分配 ───

    @PostMapping("/allocate-points")
    @Operation(summary = "分配自由属性点")
    public Result<PlayerVO> allocatePoints(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        int atk = MapUtils.getIntOrDefault(body, "atk", 0);
        int def = MapUtils.getIntOrDefault(body, "def", 0);
        int spd = MapUtils.getIntOrDefault(body, "spd", 0);
        int crit = MapUtils.getIntOrDefault(body, "crit", 0);
        int total = atk + def + spd + crit;

        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        int freePoints = toInt(stats.get("freePoints"), 0);
        if (total <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "请至少分配1点");
        if (total > freePoints) throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "自由属性点不足");

        stats.put("allocatedAtk", toInt(stats.get("allocatedAtk"), 0) + atk);
        stats.put("allocatedDef", toInt(stats.get("allocatedDef"), 0) + def);
        stats.put("allocatedSpd", toInt(stats.get("allocatedSpd"), 0) + spd);
        stats.put("allocatedCrit", toInt(stats.get("allocatedCrit"), 0) + crit);
        stats.put("freePoints", freePoints - total);
        // Update actual stats
        stats.put("attack", toInt(stats.get("attack"), 10) + atk * 2);
        stats.put("defense", toInt(stats.get("defense"), 5) + def);
        stats.put("speed", toInt(stats.get("speed"), 10) + spd);
        stats.put("critRate", toDouble(stats.get("critRate"), 0.05) + crit * 0.01);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新属性失败");
        }
        playerMapper.updateById(player);

        return Result.ok(playerService.getPlayer(playerId));
    }

    @PostMapping("/reset-allocation")
    @Operation(summary = "重置全部属性分配")
    public Result<PlayerVO> resetAllocation(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        int allocatedAtk = toInt(stats.get("allocatedAtk"), 0);
        int allocatedDef = toInt(stats.get("allocatedDef"), 0);
        int allocatedSpd = toInt(stats.get("allocatedSpd"), 0);
        int allocatedCrit = toInt(stats.get("allocatedCrit"), 0);
        int totalAlloc = allocatedAtk + allocatedDef + allocatedSpd + allocatedCrit;
        if (totalAlloc <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "没有已分配的属性点");

        int cost = Math.max(50, totalAlloc * 20);
        if (player.getCoins() < cost) throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "金币不足，需要 " + cost + " 金币");

        player.setCoins(player.getCoins() - cost);
        // Reset allocated stats
        stats.put("allocatedAtk", 0);
        stats.put("allocatedDef", 0);
        stats.put("allocatedSpd", 0);
        stats.put("allocatedCrit", 0);
        stats.put("freePoints", toInt(stats.get("freePoints"), 0) + totalAlloc);
        // Revert actual stats
        stats.put("attack", toInt(stats.get("attack"), 10) - allocatedAtk * 2);
        stats.put("defense", toInt(stats.get("defense"), 5) - allocatedDef);
        stats.put("speed", toInt(stats.get("speed"), 10) - allocatedSpd);
        stats.put("critRate", Math.max(0.01, toDouble(stats.get("critRate"), 0.05) - allocatedCrit * 0.01));

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "重置属性失败");
        }
        playerMapper.updateById(player);

        return Result.ok(playerService.getPlayer(playerId));
    }

    @PostMapping("/reset/{playerId}")
    @Operation(summary = "重置玩家进度")
    public Result<PlayerVO> resetPlayer(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        // Reset to initial state
        player.setCurrentMainChapter("main_ch01_paid_service");
        player.setCurrentChapter("ch1_01_last_train");
        player.setCurrentLocation("");
        player.setCoins(0);
        player.setStoryFragments(0);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("level", 1);
        stats.put("exp", 0);
        stats.put("hp", 100);
        stats.put("maxHp", 100);
        stats.put("attack", 10);
        stats.put("defense", 5);
        stats.put("speed", 10);
        stats.put("critRate", 0.05);
        stats.put("critDamage", 1.5);
        stats.put("stamina", 50);
        stats.put("maxStamina", 50);
        stats.put("explorationPower", 1);
        stats.put("luck", 1);
        stats.put("dropRate", 0);
        stats.put("rating", 1000);
        stats.put("pkWins", 0);
        stats.put("pkLosses", 0);
        stats.put("pkStreak", 0);
        stats.put("worldLineShift", 0);
        stats.put("channelHeat", 0);
        stats.put("freePoints", 40);
        stats.put("allocatedAtk", 0);
        stats.put("allocatedDef", 0);
        stats.put("allocatedSpd", 0);
        stats.put("allocatedCrit", 0);
        stats.put("avatarRank", "F");
        stats.put("avatarRankName", "临时化身");
        stats.put("storyGrade", "ordinary");
        stats.put("isResting", false);
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "重置状态失败");
        }

        Map<String, Object> stageProgress = new LinkedHashMap<>();
        stageProgress.put("storyEventsTriggered", new java.util.ArrayList<>());
        stageProgress.put("sideEventsTriggered", new java.util.ArrayList<>());
        stageProgress.put("bossClues", new LinkedHashMap<>());
        stageProgress.put("opportunityEventsTriggered", new java.util.ArrayList<>());
        stageProgress.put("hiddenEventsTriggered", new java.util.ArrayList<>());
        stageProgress.put("storyPity", 0);
        stageProgress.put("explorationsByLocation", new LinkedHashMap<>());
        try {
            player.setStageProgressJson(objectMapper.writeValueAsString(stageProgress));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "重置进度失败");
        }

        playerMapper.updateById(player);
        return Result.ok(playerService.getPlayer(playerId));
    }

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    // ─── 心跳 / 在线标记 ───

    @PostMapping("/heartbeat")
    @Operation(summary = "心跳 - 标记在线并检测PK挑战")
    public Result<Map<String, Object>> heartbeat(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pendingChallenges", java.util.Collections.emptyList());
        return Result.ok(result);
    }
}
