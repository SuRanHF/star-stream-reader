package com.huazhenghai.readergame.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.CreatePlayerRequest;
import com.huazhenghai.readergame.dto.RestRequest;
import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.vo.RestStateVO;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerFaction;
import com.huazhenghai.readergame.mapper.ConstellationFactionMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionMapper;
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
    private final PlayerFactionMapper playerFactionMapper;
    private final ConstellationFactionMapper constellationFactionMapper;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;

    public PlayerController(PlayerService playerService,
                            RecoveryService recoveryService,
                            PlayerMapper playerMapper,
                            PlayerFactionMapper playerFactionMapper,
                            ConstellationFactionMapper constellationFactionMapper,
                            LocationMapper locationMapper,
                            ObjectMapper objectMapper) {
        this.playerService = playerService;
        this.recoveryService = recoveryService;
        this.playerMapper = playerMapper;
        this.playerFactionMapper = playerFactionMapper;
        this.constellationFactionMapper = constellationFactionMapper;
        this.locationMapper = locationMapper;
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
    @GetMapping("/{playerId:\\d+}")
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
        vo.setHpIntervalSeconds(toInt(stats.get("hpIntervalSeconds"), 1));
        vo.setStaminaIntervalSeconds(toInt(stats.get("staminaIntervalSeconds"), 1));
        vo.setExpIntervalSeconds(toInt(stats.get("expIntervalSeconds"), 30));
        vo.setExp(toInt(stats.get("exp"), 0));
        int level = toInt(stats.get("level"), 1);
        vo.setMaxExp(100 * level * level);

        return Result.ok(vo);
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    /** 修复异常属性到基准值 (防负数/坏数据) */
    private void repairBaseStats(Map<String, Object> stats) {
        int curAttack = toInt(stats.get("attack"), 10);
        int curDefense = toInt(stats.get("defense"), 5);
        int curSpeed = toInt(stats.get("speed"), 3);
        double curCrit = toDouble(stats.get("critRate"), 0.0);
        if (curAttack < 10) stats.put("attack", 10);
        if (curDefense < 5) stats.put("defense", 5);
        if (curSpeed < 3) stats.put("speed", 3);
        if (curCrit < 0.0) stats.put("critRate", 0.0);
    }

    // ─── 地点切换 ───

    @PostMapping("/switch-location")
    @Operation(summary = "切换当前地点")
    public Result<Map<String, Object>> switchLocation(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        String locationKey = MapUtils.getStringRequired(body, "locationKey");
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        // 检查地点是否存在
        QueryWrapper<Location> lq = new QueryWrapper<>();
        lq.eq("location_key", locationKey);
        Location loc = locationMapper.selectOne(lq);
        if (loc == null) {
            throw new BusinessException(ErrorCode.LOCATION_NOT_FOUND, "该地点不存在");
        }
        // 检查解锁条件
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> unlockConds = parseJsonMap(loc.getUnlockConditionsJson());
        if (!unlockConds.isEmpty() && unlockConds.containsKey("required_level")) {
            int required = toInt(unlockConds.get("required_level"), 0);
            int level = toInt(stats.get("level"), 1);
            if (level < required)
                throw new BusinessException(ErrorCode.LOCATION_LOCKED, "该地点尚未解锁 (需要等级" + required + ")");
        }

        player.setCurrentLocation(locationKey);
        playerMapper.updateById(player);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locationKey", locationKey);
        result.put("message", "已切换地点");
        return Result.ok(result);
    }

    // ─── 星座 (背后星) ───

    // [0]constellationKey [1]nebulaKey [2]name [3]description [4]emoji [5]effects
    // 星座取自《全知读者视角》原作
    private static final Object[][] CONSTELLATION_DATA = {
        // —— 伊甸星云 ——
        {"demon_judge_of_fire", "nebula_eden", "惡魔般的火之審判者",
            "伊甸園的熾天使，以烈火審判一切不義。對化身抱有無法抑制的保護慾。", "🔥",
            new java.util.LinkedHashMap<String, Object>() {{ put("def", 15); put("maxHp", 10); }}},
        {"master_of_steel", "nebula_eden", "鋼鐵之主",
            "以千錘百煉的鋼鐵意志守護同伴。無論多少次被擊倒，都會再次站起。", "⚔",
            new java.util.LinkedHashMap<String, Object>() {{ put("def", 8); put("maxHp", 20); }}},

        // —— 流浪者星云 ——
        {"prisoner_of_golden_headband", "nebula_vagrant", "金箍棒囚徒",
            "齊天大聖，被金箍束縛卻從未屈服。跨越無數世界的劇本，尋找真正的自由。", "🐵",
            new java.util.LinkedHashMap<String, Object>() {{ put("spd", 18); put("luck", 4); }}},
        {"abyssal_black_flame_dragon", "nebula_vagrant", "深淵黑色焰龍",
            "深淵中燃燒的黑色火焰，象徵著不被馴服的力量。在黑暗中開闢自己的道路。", "🐉",
            new java.util.LinkedHashMap<String, Object>() {{ put("atk", 15); put("critRate", 0.10); }}},

        // —— 深渊观测所 ——
        {"queen_of_darkest_spring", "nebula_abyss", "最黑暗春天的女王",
            "冥界的女王，掌控死亡與重生的權能。在黑暗中孕育新的可能性。", "💀",
            new java.util.LinkedHashMap<String, Object>() {{ put("atk", 12); put("worldLineShift", 3); }}},
        {"father_of_rich_night", "nebula_abyss", "富裕夜晚之父",
            "冥界的王者，掌管無盡的財富與靈魂。以絕對的力量統御深淵。", "👑",
            new java.util.LinkedHashMap<String, Object>() {{ put("atk", 18); put("maxHp", 5); }}},

        // —— 星流档案馆 ——
        {"scribe_of_heaven", "nebula_starstream", "天堂的抄寫員",
            "記錄諸天萬界一切劇本的天使。知識即力量，每一段記錄都是武器。", "📜",
            new java.util.LinkedHashMap<String, Object>() {{ put("insight", 8); put("atk", 5); put("def", 5); }}},
        {"morning_star", "nebula_starstream", "晨星",
            "曾是最明亮的天使，追尋知識直至墜落。光明與黑暗的完美平衡。", "⭐",
            new java.util.LinkedHashMap<String, Object>() {{ put("atk", 5); put("def", 5); put("spd", 5); put("maxHp", 5); }}}
    };

    private boolean isValidConstellationKey(String key) {
        if (key == null || key.isBlank()) return false;
        for (Object[] d : CONSTELLATION_DATA) {
            if (d[0].equals(key)) return true;
        }
        return false;
    }

    @GetMapping("/constellations")
    @Operation(summary = "获取所有星座/背后星")
    public Result<Map<String, Object>> getConstellations() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (Object[] d : CONSTELLATION_DATA) {
            java.util.Map<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("key", d[0]);
            c.put("nebulaKey", d[1]);
            c.put("name", d[2]);
            c.put("title", d[2]);
            c.put("description", d[3]);
            c.put("emoji", d[4]);
            c.put("effects", d[5]);
            list.add(c);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("constellations", list);
        return Result.ok(result);
    }

    @PostMapping("/select-constellation")
    @Operation(summary = "选择背后星（首次免费）")
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
        if (!isValidConstellationKey(constellationKey)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "无效的背后星: " + constellationKey);
        }
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        String currentConstellation = (String) stats.get("constellation");
        // Only block current valid constellations. Old removed keys can be repaired here.
        if (isValidConstellationKey(currentConstellation)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "已选择过背后星，如需更换请使用更换接口");
        }
        stats.put("constellation", constellationKey);
        stats.put("constellationFavor", 100);
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新星座失败");
        }
        playerMapper.updateById(player);

        String nebulaKey = getNebulaKey(constellationKey);
        if (nebulaKey != null) {
            if (currentConstellation != null && !currentConstellation.isBlank()) {
                switchPlayerFaction(playerId, nebulaKey);
            } else {
                autoJoinFaction(playerId, nebulaKey);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("constellation", buildConstellationResult(constellationKey));
        result.put("constellationFavor", 100);
        return Result.ok(result);
    }

    @PostMapping("/change-constellation")
    @Operation(summary = "更换背后星（消耗故事碎片）")
    public Result<Map<String, Object>> changeConstellation(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        String newConstellationKey = MapUtils.getStringRequired(body, "constellationKey");
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        if (!isValidConstellationKey(newConstellationKey)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "无效的背后星: " + newConstellationKey);
        }
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        String currentConstellation = (String) stats.get("constellation");
        if (!isValidConstellationKey(currentConstellation)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "尚未选择背后星，请先使用选择接口");
        }
        if (currentConstellation.equals(newConstellationKey)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "新背后星与当前相同");
        }
        // Cost: 200 story fragments + lose 50% constellation favor
        int storyFragments = player.getStoryFragments() != null ? player.getStoryFragments() : 0;
        int cost = 200;
        if (storyFragments < cost) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "故事碎片不足，更换背后星需要 " + cost + " 碎片（当前: " + storyFragments + "）");
        }
        int currentFavor = stats.get("constellationFavor") instanceof Number
                ? ((Number) stats.get("constellationFavor")).intValue() : 0;
        int newFavor = Math.max(50, currentFavor / 2);
        player.setStoryFragments(storyFragments - cost);
        stats.put("constellation", newConstellationKey);
        stats.put("constellationFavor", newFavor);
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新星座失败");
        }
        playerMapper.updateById(player);

        // Switch faction to new constellation
        String nebulaKey = getNebulaKey(newConstellationKey);
        if (nebulaKey != null) {
            switchPlayerFaction(playerId, nebulaKey);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("constellation", buildConstellationResult(newConstellationKey));
        result.put("constellationFavor", newFavor);
        result.put("cost", cost);
        result.put("message", "已更换背后星，消耗 " + cost + " 故事碎片，星座好感降至 " + newFavor);
        return Result.ok(result);
    }

    /** Map constellation key → nebula faction key. */
    private String getNebulaKey(String constellationKey) {
        for (Object[] d : CONSTELLATION_DATA) {
            if (d[0].equals(constellationKey)) return (String) d[1];
        }
        return null;
    }

    private Map<String, Object> buildConstellationResult(String key) {
        for (Object[] d : CONSTELLATION_DATA) {
            if (d[0].equals(key)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("key", d[0]);
                c.put("nebulaKey", d[1]);
                c.put("name", d[2]);
                c.put("title", d[2]);
                c.put("description", d[3]);
                c.put("emoji", d[4]);
                c.put("effects", d[5]);
                return c;
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * Auto-join the faction corresponding to the constellation on first selection.
     */
    private void autoJoinFaction(Long playerId, String factionKey) {
        try {
            // Check for existing active faction
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlayerFaction> eq =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            eq.eq("player_id", playerId).eq("status", "active");
            PlayerFaction existing = playerFactionMapper.selectOne(eq);
            if (existing != null) return;

            // Check for previous record to reactivate
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlayerFaction> pq =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            pq.eq("player_id", playerId);
            PlayerFaction prev = playerFactionMapper.selectOne(pq);

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (prev != null) {
                prev.setFactionKey(factionKey);
                prev.setRole("member");
                prev.setReputation(0L);
                prev.setContributionTotal(0L);
                prev.setJoinedAt(now);
                prev.setLeftAt(null);
                prev.setStatus("active");
                prev.setUpdatedAt(now);
                playerFactionMapper.updateById(prev);
            } else {
                PlayerFaction pf = new PlayerFaction();
                pf.setPlayerId(playerId);
                pf.setFactionKey(factionKey);
                pf.setRole("member");
                pf.setReputation(0L);
                pf.setContributionTotal(0L);
                pf.setJoinedAt(now);
                pf.setStatus("active");
                pf.setCreatedAt(now);
                pf.setUpdatedAt(now);
                playerFactionMapper.insert(pf);
            }

            // Update faction member count
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.huazhenghai.readergame.entity.ConstellationFaction> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            uw.eq("faction_key", factionKey).setSql("member_count = member_count + 1");
            constellationFactionMapper.update(null, uw);
        } catch (Exception ignored) {
            // Non-critical: faction join failure shouldn't block constellation selection
        }
    }

    /**
     * Switch player to a new faction when changing constellation.
     */
    private void switchPlayerFaction(Long playerId, String newFactionKey) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlayerFaction> eq =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            eq.eq("player_id", playerId).eq("status", "active");
            PlayerFaction current = playerFactionMapper.selectOne(eq);
            if (current != null) {
                String oldFactionKey = current.getFactionKey();
                // Leave old faction
                current.setStatus("left");
                current.setLeftAt(java.time.LocalDateTime.now());
                current.setUpdatedAt(java.time.LocalDateTime.now());
                playerFactionMapper.updateById(current);

                // Decrement old faction member count
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.huazhenghai.readergame.entity.ConstellationFaction> dw =
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                dw.eq("faction_key", oldFactionKey).setSql("member_count = GREATEST(member_count - 1, 0)");
                constellationFactionMapper.update(null, dw);
            }

            // Join new faction
            autoJoinFaction(playerId, newFactionKey);
        } catch (Exception ignored) {
            // Non-critical
        }
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
        repairBaseStats(stats);
        int freePoints = toInt(stats.get("freePoints"), 0);
        if (total <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "请至少分配1点");
        if (total > freePoints) throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "自由属性点不足");

        stats.put("allocatedAtk", toInt(stats.get("allocatedAtk"), 0) + atk);
        stats.put("allocatedDef", toInt(stats.get("allocatedDef"), 0) + def);
        stats.put("allocatedSpd", toInt(stats.get("allocatedSpd"), 0) + spd);
        stats.put("allocatedCrit", toInt(stats.get("allocatedCrit"), 0) + crit);
        stats.put("freePoints", freePoints - total);
        // Update actual stats (1点=1属性, 暴击1点=2%=0.02)
        stats.put("attack", toInt(stats.get("attack"), 10) + atk);
        stats.put("defense", toInt(stats.get("defense"), 5) + def);
        stats.put("speed", toInt(stats.get("speed"), 3) + spd);
        stats.put("critRate", toDouble(stats.get("critRate"), 0.0) + crit * 0.02);

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
        repairBaseStats(stats);
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
        // Revert actual stats (1点=1属性, 暴击1点=0.02)
        stats.put("attack", toInt(stats.get("attack"), 10) - allocatedAtk);
        stats.put("defense", toInt(stats.get("defense"), 5) - allocatedDef);
        stats.put("speed", toInt(stats.get("speed"), 3) - allocatedSpd);
        stats.put("critRate", Math.max(0.0, toDouble(stats.get("critRate"), 0.0) - allocatedCrit * 0.02));

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
        Map<String, Object> oldStats = parseJsonMap(player.getStatsJson());
        Object constellation = oldStats.get("constellation");
        Object constellationFavor = oldStats.getOrDefault("constellationFavor", 100);
        stats.put("level", 1);
        stats.put("exp", 0);
        stats.put("hp", 100);
        stats.put("maxHp", 100);
        stats.put("attack", 10);
        stats.put("defense", 5);
        stats.put("speed", 3);
        stats.put("critRate", 0.0);
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
        stats.put("freePoints", 10);
        stats.put("allocatedAtk", 0);
        stats.put("allocatedDef", 0);
        stats.put("allocatedSpd", 0);
        stats.put("allocatedCrit", 0);
        stats.put("avatarRank", "F");
        stats.put("avatarRankName", "临时化身");
        stats.put("storyGrade", "ordinary");
        stats.put("isResting", false);
        if (isValidConstellationKey(String.valueOf(constellation))) {
            stats.put("constellation", constellation);
            stats.put("constellationFavor", constellationFavor);
        }
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
