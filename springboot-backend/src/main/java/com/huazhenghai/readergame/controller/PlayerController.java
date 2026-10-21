package com.huazhenghai.readergame.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.dto.CreatePlayerRequest;
import com.huazhenghai.readergame.dto.ResetAllocationRequest;
import com.huazhenghai.readergame.dto.ReviveRequest;
import com.huazhenghai.readergame.dto.PeerReviveRequest;
import com.huazhenghai.readergame.dto.RestRequest;
import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.vo.RestStateVO;
import com.huazhenghai.readergame.vo.PkChallengeVO;
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
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.PkService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerStaminaProjectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    private final PlayerMaxHpProjectionService maxHpProjectionService;
    private final PlayerMapper playerMapper;
    private final PlayerFactionMapper playerFactionMapper;
    private final ConstellationFactionMapper constellationFactionMapper;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;
    private final PkService pkService;
    private final PlayerLogService playerLogService;
    private final PlayerActionReceiptService actionReceiptService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final PlayerStaminaProjectionService staminaProjectionService;

    public PlayerController(PlayerService playerService,
                            RecoveryService recoveryService,
                            PlayerMaxHpProjectionService maxHpProjectionService,
                            PlayerMapper playerMapper,
                            PlayerFactionMapper playerFactionMapper,
                            ConstellationFactionMapper constellationFactionMapper,
                            LocationMapper locationMapper,
                            ObjectMapper objectMapper,
                            PkService pkService,
                            PlayerLogService playerLogService,
                            PlayerActionReceiptService actionReceiptService,
                            PlayerResourceTransactionService resourceTransactionService,
                            PlayerStaminaProjectionService staminaProjectionService) {
        this.playerService = playerService;
        this.recoveryService = recoveryService;
        this.maxHpProjectionService = maxHpProjectionService;
        this.playerMapper = playerMapper;
        this.playerFactionMapper = playerFactionMapper;
        this.constellationFactionMapper = constellationFactionMapper;
        this.locationMapper = locationMapper;
        this.objectMapper = objectMapper;
        this.pkService = pkService;
        this.playerLogService = playerLogService;
        this.actionReceiptService = actionReceiptService;
        this.resourceTransactionService = resourceTransactionService;
        this.staminaProjectionService = staminaProjectionService;
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
        applyEffectiveMaxStamina(vo, player.getId());
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
        applyEffectiveMaxStamina(vo, playerId);
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
        vo.setMaxStamina(staminaProjectionService.projectMaxStamina(
                playerId, toInt(stats.get("maxStamina"), 50)));
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

    private void applyEffectiveMaxStamina(PlayerVO playerVO, Long playerId) {
        if (playerVO == null || playerVO.getStats() == null) return;
        Map<String, Object> stats = new LinkedHashMap<>(playerVO.getStats());
        int storedMax = toInt(stats.get("maxStamina"), 50);
        stats.put("maxStamina", staminaProjectionService.projectMaxStamina(playerId, storedMax));
        playerVO.setStats(stats);
    }

    private int readExactIntOrDefault(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return 0;
        try {
            return new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT, key + " 必须是 32 位整数");
        }
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
        maxHpProjectionService.normalizeForPersistence(stats);
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
    @Transactional
    public Result<Map<String, Object>> changeConstellation(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        String newConstellationKey = MapUtils.getStringRequired(body, "constellationKey");
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectByIdForUpdate(playerId);
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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    "故事碎片不足，更换背后星需要 " + cost + " 故事碎片（当前: " + storyFragments + "）");
        }
        int currentFavor = stats.get("constellationFavor") instanceof Number
                ? ((Number) stats.get("constellationFavor")).intValue() : 0;
        int newFavor = Math.max(50, currentFavor / 2);
        Map<String, Object> logMetadata = new LinkedHashMap<>();
        logMetadata.put("fromConstellation", currentConstellation);
        logMetadata.put("toConstellation", newConstellationKey);
        logMetadata.put("resource", "storyFragments");
        logMetadata.put("cost", cost);
        logMetadata.put("favorBefore", currentFavor);
        logMetadata.put("favorAfter", newFavor);
        int storyFragmentsAfter = ResourceBalance.applyDelta("故事碎片", storyFragments, -cost);
        player.setStoryFragments(storyFragmentsAfter);
        maxHpProjectionService.normalizeForPersistence(stats);
        stats.put("constellation", newConstellationKey);
        stats.put("constellationFavor", newFavor);
        maxHpProjectionService.normalizeForPersistence(stats);
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新星座失败");
        }
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存背后星更换和故事碎片扣除失败");

        logMetadata.put("storyFragmentsBefore", storyFragments);
        logMetadata.put("storyFragmentsAfter", storyFragmentsAfter);
        String transactionGroupId = "constellation-change:" + playerId + ":" + UUID.randomUUID();
        boolean ledgerInserted = resourceTransactionService.record(
                new PlayerResourceTransactionService.Change(
                        playerId, "currency", "story_fragments", -cost,
                        storyFragments, storyFragmentsAfter, "constellation_change",
                        newConstellationKey, transactionGroupId,
                        transactionGroupId + ":story_fragments", logMetadata));
        if (!ledgerInserted) throw new IllegalStateException("背后星更换故事碎片流水写入失败");

        Map<String, Object> previousConstellation = buildConstellationResult(currentConstellation);
        Map<String, Object> nextConstellation = buildConstellationResult(newConstellationKey);
        String logMessage = "更换背后星「" + previousConstellation.get("name") + "」→「"
                + nextConstellation.get("name") + "」，消耗 " + cost + " 故事碎片，好感度 "
                + currentFavor + "→" + newFavor;
        playerLogService.addLog(playerId, "constellation_change", logMessage, logMetadata);

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
    @Transactional
    public Result<Map<String, Object>> revivePlayer(@Valid @RequestBody ReviveRequest request) {
        Long playerId = request.getPlayerId();
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        String method = request.getMethod();
        if (method == null) method = "coins";
        if (!"coins".equals(method) && !"title".equals(method))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "复活方式不合法");

        String requestKey = request.getRequestKey();
        Map<String, Object> requestPayload = Map.of("playerId", playerId, "method", method);
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "player.revive", requestKey, requestPayload, Map.class);
        if (replay != null) return Result.ok(replay);

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        if (!Boolean.TRUE.equals(stats.get("isDead"))) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "玩家未死亡");
        }

        int coinsBefore = player.getCoins() != null ? player.getCoins() : 0;
        int coinsAfter = coinsBefore;
        int cost = 0;
        int level = Math.max(1, toInt(stats.get("level"), 1));
        if ("coins".equals(method)) {
            cost = Math.multiplyExact(100, level);
            if (coinsBefore < cost) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                        "星币不足，需要 " + cost + " 星币");
            }
            coinsAfter = Math.subtractExact(coinsBefore, cost);
            player.setCoins(coinsAfter);
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
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存复活状态失败");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "你从冥界归来了。HP已恢复至" + (maxHp / 2) + "点，请休息恢复更多生命值。");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", method);
        metadata.put("level", level);
        metadata.put("hpAfter", maxHp / 2);
        if (cost > 0) {
            metadata.put("cost", cost);
            metadata.put("coinsBefore", coinsBefore);
            metadata.put("coinsAfter", coinsAfter);
            String transactionGroupId = "revive:" + playerId + ":" + UUID.nameUUIDFromBytes(
                    (playerId + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
            resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                    playerId, "currency", "coins", -cost, coinsBefore, coinsAfter,
                    "self_revive", String.valueOf(playerId), transactionGroupId,
                    transactionGroupId + ":coins", metadata));
        }
        playerLogService.addLog(playerId, "player_revive", result.get("message").toString(), metadata);
        actionReceiptService.record(playerId, "player.revive", requestKey, requestPayload, result);
        return Result.ok(result);
    }

    @PostMapping("/peer-revive")
    @Operation(summary = "复活其他玩家")
    @Transactional
    public Result<Map<String, Object>> peerRevive(@Valid @RequestBody PeerReviveRequest request) {
        Long reviverId = request.getReviverId();
        Long targetId = request.getTargetId();
        if (Objects.equals(reviverId, targetId))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "不能通过援救接口复活自己");

        Long userId = LoginUserContext.get().getUserId();
        Long firstPlayerId = Math.min(reviverId, targetId);
        Long secondPlayerId = Math.max(reviverId, targetId);
        Player firstPlayer = playerMapper.selectByIdForUpdate(firstPlayerId);
        Player secondPlayer = playerMapper.selectByIdForUpdate(secondPlayerId);
        Player reviver = Objects.equals(firstPlayerId, reviverId) ? firstPlayer : secondPlayer;
        Player target = Objects.equals(firstPlayerId, targetId) ? firstPlayer : secondPlayer;
        if (reviver == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "施救者不存在");
        if (!Objects.equals(reviver.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此施救者");
        if (target == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "目标玩家不存在");

        String method = request.getMethod();
        if (method == null) method = "coins";
        if (!"coins".equals(method) && !"title".equals(method))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "复活方式不合法");
        String requestKey = request.getRequestKey();
        Map<String, Object> requestPayload = Map.of(
                "reviverId", reviverId, "targetId", targetId, "method", method);
        Map<String, Object> replay = actionReceiptService.findReplay(
                reviverId, "player.peer-revive", requestKey, requestPayload, Map.class);
        if (replay != null) return Result.ok(replay);

        Map<String, Object> targetStats = parseJsonMap(target.getStatsJson());
        if (!Boolean.TRUE.equals(targetStats.get("isDead"))) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "目标玩家未死亡");
        }

        int coinsBefore = reviver.getCoins() != null ? reviver.getCoins() : 0;
        int coinsAfter = coinsBefore;
        int cost = 0;
        int level = Math.max(1, toInt(targetStats.get("level"), 1));
        if ("coins".equals(method)) {
            String constellation = String.valueOf(targetStats.getOrDefault("constellation", ""));
            double multiplier = "queen_of_darkest_spring".equals(constellation) ? 0.5 : 1.0;
            cost = Math.toIntExact(Math.round(100L * level * multiplier));
            if (coinsBefore < cost) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "星币不足，需要 " + cost + " 星币");
            }
            coinsAfter = Math.subtractExact(coinsBefore, cost);
            reviver.setCoins(coinsAfter);
        }

        targetStats.put("isDead", false);
        int maxHp = toInt(targetStats.get("maxHp"), 100);
        int hpAfter = maxHp / 2;
        targetStats.put("hp", hpAfter);
        try {
            target.setStatsJson(objectMapper.writeValueAsString(targetStats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新状态失败");
        }
        if (playerMapper.updateById(target) <= 0)
            throw new IllegalStateException("保存被援救玩家的复活状态失败");
        if (cost > 0 && playerMapper.updateById(reviver) <= 0)
            throw new IllegalStateException("保存施救者的星币扣除失败");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "你从冥界拉回了一位玩家。");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", method);
        metadata.put("reviverId", reviverId);
        metadata.put("targetId", targetId);
        metadata.put("targetLevel", level);
        metadata.put("hpAfter", hpAfter);
        if (cost > 0) {
            metadata.put("cost", cost);
            metadata.put("coinsBefore", coinsBefore);
            metadata.put("coinsAfter", coinsAfter);
            String transactionGroupId = "peer-revive:" + reviverId + ":" + UUID.nameUUIDFromBytes(
                    (reviverId + ":" + targetId + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
            resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                    reviverId, "currency", "coins", -cost, coinsBefore, coinsAfter,
                    "peer_revive", String.valueOf(targetId), transactionGroupId,
                    transactionGroupId + ":coins", metadata));
        }
        String reviverLog = "你援救了「" + target.getPlayerName() + "」";
        if (cost > 0) reviverLog += "，消耗 " + cost + " 星币";
        playerLogService.addLog(reviverId, "peer_revive_sent", reviverLog, metadata);
        playerLogService.addLog(targetId, "peer_revived", "「"
                + reviver.getPlayerName() + "」将你从冥界拉回。", metadata);
        actionReceiptService.record(reviverId, "player.peer-revive", requestKey, requestPayload, result);
        return Result.ok(result);
    }

    // ─── 属性分配 ───

    @PostMapping("/allocate-points")
    @Operation(summary = "分配自由属性点")
    @Transactional
    public Result<PlayerVO> allocatePoints(@RequestBody Map<String, Object> body) {
        Long playerId = MapUtils.getLong(body, "playerId");
        Long userId = LoginUserContext.get().getUserId();
        String requestKey = MapUtils.getStringRequired(body, "requestKey");
        if (requestKey.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 长度不能超过 64");

        int atk = readExactIntOrDefault(body, "atk");
        int def = readExactIntOrDefault(body, "def");
        int spd = readExactIntOrDefault(body, "spd");
        int crit = readExactIntOrDefault(body, "crit");

        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> requestPayload = Map.of(
                "playerId", playerId, "atk", atk, "def", def, "spd", spd, "crit", crit);
        PlayerVO replay = actionReceiptService.findReplay(
                playerId, "player.allocate-points", requestKey, requestPayload, PlayerVO.class);
        if (replay != null) return Result.ok(replay);

        if (atk < 0 || def < 0 || spd < 0 || crit < 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "分配属性点不能为负数");
        long total = (long) atk + def + spd + crit;

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        repairBaseStats(stats);
        int freePoints = toInt(stats.get("freePoints"), 0);
        if (total <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "请至少分配1点");
        if (total > freePoints) throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "自由属性点不足");

        stats.put("allocatedAtk", ResourceBalance.applySignedDelta(
                "攻击分配点", toInt(stats.get("allocatedAtk"), 0), atk));
        stats.put("allocatedDef", ResourceBalance.applySignedDelta(
                "防御分配点", toInt(stats.get("allocatedDef"), 0), def));
        stats.put("allocatedSpd", ResourceBalance.applySignedDelta(
                "速度分配点", toInt(stats.get("allocatedSpd"), 0), spd));
        stats.put("allocatedCrit", ResourceBalance.applySignedDelta(
                "暴击分配点", toInt(stats.get("allocatedCrit"), 0), crit));
        int pointsSpent = Math.toIntExact(total);
        int freePointsAfter = ResourceBalance.applyDelta("自由属性点", freePoints, -pointsSpent);
        stats.put("freePoints", freePointsAfter);
        // Update actual stats (1点=1属性, 暴击1点=2%=0.02)
        stats.put("attack", ResourceBalance.applySignedDelta("攻击", toInt(stats.get("attack"), 10), atk));
        stats.put("defense", ResourceBalance.applySignedDelta("防御", toInt(stats.get("defense"), 5), def));
        stats.put("speed", ResourceBalance.applySignedDelta("速度", toInt(stats.get("speed"), 3), spd));
        stats.put("critRate", toDouble(stats.get("critRate"), 0.0) + crit * 0.02);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新属性失败");
        }
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存属性分配失败");

        String transactionGroupId = "alloc:" + UUID.nameUUIDFromBytes(
                (playerId + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> resourceMetadata = Map.of(
                "requestKey", requestKey, "atk", atk, "def", def, "spd", spd, "crit", crit);
        boolean ledgerInserted = resourceTransactionService.record(
                new PlayerResourceTransactionService.Change(
                        playerId, "progress", "free_points", -pointsSpent, freePoints, freePointsAfter,
                        "attribute_allocation", String.valueOf(playerId), transactionGroupId,
                        transactionGroupId + ":free-points", resourceMetadata));
        if (!ledgerInserted) throw new IllegalStateException("记录自由属性点支出流水失败");

        String message = "已分配属性点：攻击 " + atk + "、防御 " + def + "、速度 " + spd + "、暴击 " + crit;
        Map<String, Object> logMetadata = new LinkedHashMap<>(resourceMetadata);
        logMetadata.put("kind", "attribute_allocate");
        logMetadata.put("freePointsBefore", freePoints);
        logMetadata.put("freePointsAfter", freePointsAfter);
        playerLogService.addLog(playerId, "attribute_allocate", message, logMetadata);

        PlayerVO result = playerService.getPlayer(playerId);
        actionReceiptService.record(playerId, "player.allocate-points", requestKey, requestPayload, result);
        return Result.ok(result);
    }

    @PostMapping("/reset-allocation")
    @Operation(summary = "重置全部属性分配")
    @Transactional
    public Result<PlayerVO> resetAllocation(@Valid @RequestBody ResetAllocationRequest request) {
        Long playerId = request.getPlayerId();
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        String requestKey = request.getRequestKey();
        Map<String, Object> requestPayload = Map.of("playerId", playerId);
        PlayerVO replay = actionReceiptService.findReplay(
                playerId, "player.reset-allocation", requestKey, requestPayload, PlayerVO.class);
        if (replay != null) return Result.ok(replay);

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        repairBaseStats(stats);
        int allocatedAtk = toInt(stats.get("allocatedAtk"), 0);
        int allocatedDef = toInt(stats.get("allocatedDef"), 0);
        int allocatedSpd = toInt(stats.get("allocatedSpd"), 0);
        int allocatedCrit = toInt(stats.get("allocatedCrit"), 0);
        if (allocatedAtk < 0 || allocatedDef < 0 || allocatedSpd < 0 || allocatedCrit < 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "属性分配数据无效，无法重置");
        long totalAllocLong = (long) allocatedAtk + allocatedDef + allocatedSpd + allocatedCrit;
        if (totalAllocLong <= 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "没有已分配的属性点");
        if (totalAllocLong > Integer.MAX_VALUE)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "属性分配数据超出可重置范围");
        int totalAlloc = (int) totalAllocLong;

        long costLong = Math.max(50L, totalAllocLong * 20L);
        if (costLong > Integer.MAX_VALUE)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "属性重置费用超出可保存范围");
        int cost = (int) costLong;
        int coinsBefore = player.getCoins() != null ? player.getCoins() : 0;
        if (coinsBefore < cost)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "星币不足，需要 " + cost + " 星币");

        int freePointsBefore = toInt(stats.get("freePoints"), 0);
        if (freePointsBefore < 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "自由属性点数据无效，无法重置");
        int freePointsAfter = ResourceBalance.applyDelta("自由属性点", freePointsBefore, totalAllocLong);
        int coinsAfter = ResourceBalance.applyDelta("星币", coinsBefore, -(long) cost);
        player.setCoins(coinsAfter);
        // Reset allocated stats
        stats.put("allocatedAtk", 0);
        stats.put("allocatedDef", 0);
        stats.put("allocatedSpd", 0);
        stats.put("allocatedCrit", 0);
        stats.put("freePoints", freePointsAfter);
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
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存属性重置与星币扣除失败");

        String transactionGroupId = "respec:" + playerId + ":" + UUID.nameUUIDFromBytes(
                (playerId + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> resourceMetadata = new LinkedHashMap<>();
        resourceMetadata.put("allocatedAtk", allocatedAtk);
        resourceMetadata.put("allocatedDef", allocatedDef);
        resourceMetadata.put("allocatedSpd", allocatedSpd);
        resourceMetadata.put("allocatedCrit", allocatedCrit);
        resourceMetadata.put("totalPoints", totalAlloc);
        resourceMetadata.put("cost", cost);
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, "currency", "coins", -cost, coinsBefore, coinsAfter,
                "attribute_respec", String.valueOf(playerId), transactionGroupId,
                transactionGroupId + ":coins", resourceMetadata));

        Map<String, Object> logMetadata = new LinkedHashMap<>(resourceMetadata);
        logMetadata.put("coinsBefore", coinsBefore);
        logMetadata.put("coinsAfter", coinsAfter);
        playerLogService.addLog(playerId, "attribute_reset",
                "重置属性分配 " + totalAlloc + " 点，消耗 " + cost + " 星币", logMetadata);

        PlayerVO result = playerService.getPlayer(playerId);
        actionReceiptService.record(playerId, "player.reset-allocation", requestKey, requestPayload, result);
        return Result.ok(result);
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
        stats.put(PlayerMaxHpProjectionService.APPLIED_CONSTELLATION_BONUS_KEY, 0);
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
        List<PkChallengeVO> pendingChallenges = pkService.getPendingChallenges(playerId, userId);
        result.put("pendingChallenges", pendingChallenges);
        return Result.ok(result);
    }
}
