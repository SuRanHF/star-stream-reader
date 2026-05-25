package com.huazhenghai.readergame.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.FeedbackService;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final ItemMapper itemMapper;
    private final EquipmentMapper equipmentMapper;
    private final SkillMapper skillMapper;
    private final TitleMapper titleMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PlayerEquipmentMapper playerEquipmentMapper;
    private final PlayerSkillMapper playerSkillMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final FeedbackService feedbackService;
    private final AdminActionLogMapper adminActionLogMapper;
    private final AvatarRankService avatarRankService;
    private final MainChapterMapper mainChapterMapper;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;
    private final EquipmentService equipmentService;

    @Value("${admin.key:}")
    private String adminKey;

    public AdminController(PlayerMapper playerMapper,
                           PlayerLogMapper playerLogMapper,
                           ItemMapper itemMapper,
                           EquipmentMapper equipmentMapper,
                           SkillMapper skillMapper,
                           TitleMapper titleMapper,
                           PlayerInventoryMapper playerInventoryMapper,
                           PlayerEquipmentMapper playerEquipmentMapper,
                           PlayerSkillMapper playerSkillMapper,
                           PlayerTitleMapper playerTitleMapper,
                           FeedbackService feedbackService,
                           AdminActionLogMapper adminActionLogMapper,
                           AvatarRankService avatarRankService,
                           MainChapterMapper mainChapterMapper,
                           LocationMapper locationMapper,
                           ObjectMapper objectMapper,
                           WebSocketSessionManager sessionManager,
                           EquipmentService equipmentService) {
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.itemMapper = itemMapper;
        this.equipmentMapper = equipmentMapper;
        this.skillMapper = skillMapper;
        this.titleMapper = titleMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.playerSkillMapper = playerSkillMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.feedbackService = feedbackService;
        this.adminActionLogMapper = adminActionLogMapper;
        this.avatarRankService = avatarRankService;
        this.mainChapterMapper = mainChapterMapper;
        this.locationMapper = locationMapper;
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.equipmentService = equipmentService;
    }

    // ─── 认证 ───

    private String checkAdmin(HttpServletRequest request) {
        LoginUser user = LoginUserContext.get();
        if (user != null && "admin".equals(user.getRole())) {
            return user.getUsername() != null ? user.getUsername() : "admin";
        }
        String key = request.getHeader("X-Admin-Key");
        if (adminKey != null && adminKey.length() >= 8 && adminKey.equals(key)) {
            return "admin-key";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> Result<T> forbidden() {
        return (Result<T>) Result.fail("FORBIDDEN", "需要管理员权限或有效的 ADMIN_KEY");
    }

    // ─── 实时推送玩家更新 ───

    private void pushStatsUpdated(Long playerId) {
        try {
            Player p = playerMapper.selectById(playerId);
            if (p == null) return;
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "stats.updated");
            msg.put("data", buildPlayerFull(p));
            sessionManager.sendToPlayer(playerId, msg);
        } catch (Exception e) {
            log.warn("Failed to push stats updated to player {}: {}", playerId, e.getMessage());
        }
    }

    // ─── 日志 ───

    private void logAction(String adminName, String action, Long targetId, Map<String, Object> details) {
        try {
            AdminActionLog entry = new AdminActionLog();
            entry.setAction(action);
            entry.setTargetType("player");
            entry.setTargetId(targetId);
            try { entry.setDetails(objectMapper.writeValueAsString(details)); } catch (Exception ignored) {}
            entry.setCreatedAt(LocalDateTime.now());
            adminActionLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("记录管理员操作日志失败", e);
        }
    }

    private void addPlayerLog(Long playerId, String message) {
        PlayerLog pl = new PlayerLog();
        pl.setPlayerId(playerId);
        pl.setMessage(message);
        pl.setType("admin");
        pl.setCreatedAt(LocalDateTime.now());
        playerLogMapper.insert(pl);
    }

    // ─── 1. 玩家列表 ───

    @GetMapping("/players")
    public Result<Map<String, Object>> listPlayers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        QueryWrapper<Player> query = new QueryWrapper<>();
        if (search != null && !search.isBlank()) {
            query.and(w -> w.like("player_name", search)
                    .or().eq("id", parseInt(search)));
        }
        query.orderByDesc("id").last("LIMIT " + offset + "," + limit);

        List<Player> players = playerMapper.selectList(query);
        Long total = playerMapper.selectCount(new QueryWrapper<>());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Player p : players) {
            Map<String, Object> stats = parseJsonMap(p.getStatsJson());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("player_name", p.getPlayerName());
            item.put("level", toInt(stats.get("level"), 1));
            item.put("hp", toInt(stats.get("hp"), 0));
            item.put("maxHp", toInt(stats.get("maxHp"), 100));
            item.put("stamina", toInt(stats.get("stamina"), 0));
            item.put("maxStamina", toInt(stats.get("maxStamina"), 50));
            item.put("attack", toInt(stats.get("attack"), 10));
            item.put("defense", toInt(stats.get("defense"), 5));
            item.put("speed", toInt(stats.get("speed"), 10));
            item.put("critRate", toDouble(stats.get("critRate"), 0.05));
            item.put("coins", p.getCoins() != null ? p.getCoins() : 0);
            item.put("story_fragments", p.getStoryFragments() != null ? p.getStoryFragments() : 0);
            item.put("exp", toInt(stats.get("exp"), 0));
            item.put("freePoints", toInt(stats.get("freePoints"), 0));
            item.put("constellation", stats.get("constellation"));
            item.put("avatarRank", stats.getOrDefault("avatarRank", "F"));
            item.put("avatarRankName", stats.getOrDefault("avatarRankName", "临时化身"));
            item.put("storyGrade", stats.getOrDefault("storyGrade", "ordinary"));
            item.put("channelHeat", toInt(stats.get("channelHeat"), 0));
            item.put("isDead", Boolean.TRUE.equals(stats.get("isDead")));
            item.put("isResting", Boolean.TRUE.equals(stats.get("isResting")));
            item.put("current_chapter", p.getCurrentChapter());
            item.put("current_main_chapter", p.getCurrentMainChapter());
            item.put("current_location", p.getCurrentLocation());
            item.put("user_id", p.getUserId());
            item.put("created_at", p.getCreatedAt());
            result.add(item);
        }

        return Result.ok(Map.of("players", result, "total", total));
    }

    // ─── 2. 玩家详情 ───

    @GetMapping("/players/{id}")
    public Result<Map<String, Object>> getPlayer(@PathVariable Long id, HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        return Result.ok(Map.of("player", buildPlayerFull(p)));
    }

    // ─── 3. 快速更新 ───

    @PostMapping("/players/{id}/update")
    public Result<Map<String, Object>> updatePlayer(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        Map<String, Object> stats = parseJsonMap(p.getStatsJson());
        String[] allowedStats = {"hp", "maxHp", "stamina", "maxStamina", "attack", "defense",
                "speed", "critRate", "critDamage", "level", "exp", "freePoints", "luck",
                "channelHeat", "insight", "willpower", "leadership", "bond"};
        boolean statsChanged = false;
        for (String key : allowedStats) {
            if (body.containsKey(key)) {
                stats.put(key, toNumber(body.get(key), toInt(stats.get(key), 0)));
                statsChanged = true;
            }
        }
        if (body.containsKey("constellation")) {
            stats.put("constellation", body.get("constellation"));
            statsChanged = true;
        }
        if (body.containsKey("isDead")) {
            stats.put("isDead", Boolean.TRUE.equals(body.get("isDead")));
            statsChanged = true;
        }
        if (body.containsKey("isResting")) {
            stats.put("isResting", Boolean.TRUE.equals(body.get("isResting")));
            statsChanged = true;
        }
        if (body.containsKey("avatarRank")) {
            String rank = (String) body.get("avatarRank");
            stats.put("avatarRank", rank);
            stats.put("avatarRankName", rankName(rank));
            statsChanged = true;
        }
        if (body.containsKey("storyGrade")) {
            stats.put("storyGrade", body.get("storyGrade"));
            statsChanged = true;
        }

        Set<String> changedFields = new HashSet<>();
        if (statsChanged) {
            changedFields.add("stats_json");
            try { p.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
        }
        if (body.containsKey("coins")) {
            p.setCoins(toInt(body.get("coins"), 0));
            changedFields.add("coins");
        }
        if (body.containsKey("story_fragments")) {
            p.setStoryFragments(toInt(body.get("story_fragments"), 0));
            changedFields.add("story_fragments");
        }

        if (changedFields.isEmpty()) {
            return Result.fail("NO_CHANGES", "没有提供有效的字段");
        }

        playerMapper.updateById(p);
        addPlayerLog(id, "[管理员] 数据已被调整");
        logAction(admin, "update_player", id, Map.of("fields", changedFields));
        pushStatsUpdated(id);

        return Result.ok(Map.of("player", buildPlayerFull(p), "changed", changedFields));
    }

    // ─── 4. 强制复活 ───

    @PostMapping("/players/{id}/force-revive")
    public Result<Map<String, Object>> forceRevive(@PathVariable Long id, HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        Map<String, Object> stats = parseJsonMap(p.getStatsJson());
        stats.put("isDead", false);
        stats.put("hp", toInt(stats.get("maxHp"), 100));
        try { p.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
        playerMapper.updateById(p);
        addPlayerLog(id, "[管理员] 强制复活");
        logAction(admin, "force_revive", id, Map.of());
        pushStatsUpdated(id);

        return Result.ok(Map.of("player", buildPlayerFull(p)));
    }

    // ─── 5. 属性更新 ───

    @PatchMapping("/players/{id}/stats")
    public Result<Map<String, Object>> updateStats(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        Map<String, Object> stats = parseJsonMap(p.getStatsJson());
        String[] allowedStats = {"hp", "maxHp", "stamina", "maxStamina", "attack", "defense",
                "speed", "critRate", "critDamage", "level", "exp", "freePoints", "luck",
                "channelHeat", "insight", "willpower", "leadership", "bond"};
        String[] stringStats = {"avatarRank", "storyGrade"};
        List<String> changed = new ArrayList<>();

        for (String key : allowedStats) {
            if (body.containsKey(key)) {
                Object val = body.get(key);
                if (!(val instanceof Number)) {
                    return Result.fail("INVALID_VALUE", key + " 必须为非负数字");
                }
                double dv = ((Number) val).doubleValue();
                if (dv < 0) return Result.fail("INVALID_VALUE", key + " 必须为非负数字");
                if (key.equals("critRate") || key.equals("critDamage")) {
                    stats.put(key, dv);
                } else {
                    stats.put(key, ((Number) val).intValue());
                }
                changed.add(key);
            }
        }
        for (String key : stringStats) {
            if (body.containsKey(key)) {
                stats.put(key, body.get(key));
                if ("avatarRank".equals(key)) {
                    stats.put("avatarRankName", rankName((String) body.get(key)));
                }
                changed.add(key);
            }
        }

        if (stats.containsKey("hp") && stats.containsKey("maxHp")) {
            if (toInt(stats.get("hp"), 0) > toInt(stats.get("maxHp"), 100))
                stats.put("hp", stats.get("maxHp"));
        }
        if (stats.containsKey("stamina") && stats.containsKey("maxStamina")) {
            if (toInt(stats.get("stamina"), 0) > toInt(stats.get("maxStamina"), 50))
                stats.put("stamina", stats.get("maxStamina"));
        }

        if (changed.isEmpty()) return Result.fail("NO_CHANGES", "没有提供有效的属性字段");

        try { p.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
        playerMapper.updateById(p);
        addPlayerLog(id, "[管理员] 属性已调整: " + String.join(", ", changed));
        logAction(admin, "update_stats", id, Map.of("changed", changed));
        pushStatsUpdated(id);

        return Result.ok(Map.of("player", buildPlayerFull(p), "changed", changed));
    }

    // ─── 6. 资源更新 ───

    @PatchMapping("/players/{id}/resources")
    public Result<Map<String, Object>> updateResources(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        List<String> changed = new ArrayList<>();

        if (body.containsKey("coins")) {
            int val = toInt(body.get("coins"), 0);
            if (val < 0) return Result.fail("INVALID_VALUE", "coins 必须为非负整数");
            p.setCoins(val);
            changed.add("coins");
        }
        if (body.containsKey("story_fragments")) {
            int val = toInt(body.get("story_fragments"), 0);
            if (val < 0) return Result.fail("INVALID_VALUE", "story_fragments 必须为非负整数");
            p.setStoryFragments(val);
            changed.add("story_fragments");
        }
        if (body.containsKey("constellationFavor")) {
            int val = toInt(body.get("constellationFavor"), 0);
            if (val < 0) return Result.fail("INVALID_VALUE", "constellationFavor 必须为非负数字");
            p.setConstellationFavor(val);
            changed.add("constellationFavor");
        }
        if (body.containsKey("abyssMark")) {
            int val = toInt(body.get("abyssMark"), 0);
            if (val < 0) return Result.fail("INVALID_VALUE", "abyssMark 必须为非负数字");
            p.setAbyssMark(val);
            changed.add("abyssMark");
        }

        if (changed.isEmpty()) return Result.fail("NO_CHANGES", "没有提供有效的资源字段");

        playerMapper.updateById(p);
        addPlayerLog(id, "[管理员] 资源已调整: " + String.join(", ", changed));
        logAction(admin, "update_resources", id, Map.of("changed", changed));
        pushStatsUpdated(id);

        return Result.ok(Map.of("player", buildPlayerFull(p), "changed", changed));
    }

    // ─── 7. 进度更新 ───

    @PatchMapping("/players/{id}/progress")
    public Result<Map<String, Object>> updateProgress(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body,
                                                       HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        List<String> changed = new ArrayList<>();

        if (body.containsKey("current_chapter")) {
            p.setCurrentChapter((String) body.get("current_chapter"));
            changed.add("current_chapter");
        }
        if (body.containsKey("current_main_chapter")) {
            p.setCurrentMainChapter((String) body.get("current_main_chapter"));
            changed.add("current_main_chapter");
        }
        if (body.containsKey("current_location")) {
            p.setCurrentLocation((String) body.get("current_location"));
            changed.add("current_location");
        }
        if (body.containsKey("current_stage")) {
            p.setCurrentStage((String) body.get("current_stage"));
            changed.add("current_stage");
        }
        if (body.containsKey("stage_progress_json")) {
            Object spj = body.get("stage_progress_json");
            String spjStr = spj instanceof String ? (String) spj : spj.toString();
            // validate it's valid JSON
            try {
                objectMapper.readTree(spjStr);
                p.setStageProgressJson(spjStr);
                changed.add("stage_progress_json");
            } catch (Exception e) {
                return Result.fail("INVALID_JSON", "stage_progress_json 不是有效的JSON");
            }
        }

        if (changed.isEmpty()) return Result.fail("NO_CHANGES", "没有提供有效的进度字段");

        playerMapper.updateById(p);
        addPlayerLog(id, "[管理员] 进度已调整: " + String.join(", ", changed));
        logAction(admin, "update_progress", id, Map.of("changed", changed));

        return Result.ok(Map.of("player", buildPlayerFull(p), "changed", changed));
    }

    // ─── 7.5. 发放道具 — 可选项列表 ───

    @GetMapping("/grant-options")
    public Result<Map<String, Object>> getGrantOptions(HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        List<Map<String, Object>> items = itemMapper.selectList(null).stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", i.getItemKey());
            m.put("name", i.getName() != null ? i.getName() : i.getItemKey());
            m.put("type", i.getItemType());
            m.put("rarity", i.getRarity());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> equipments = equipmentMapper.selectList(null).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.getEquipmentKey());
            m.put("name", e.getName() != null ? e.getName() : e.getEquipmentKey());
            m.put("slot", e.getSlot());
            m.put("rarity", e.getRarity());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> skills = skillMapper.selectList(null).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", s.getSkillKey());
            m.put("name", s.getName() != null ? s.getName() : s.getSkillKey());
            m.put("type", s.getType());
            m.put("rarity", s.getRarity());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> titles = titleMapper.selectList(null).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", t.getTitleKey());
            m.put("name", t.getName() != null ? t.getName() : t.getTitleKey());
            m.put("rarity", t.getRarity());
            return m;
        }).collect(Collectors.toList());

        return Result.ok(Map.of("items", items, "equipments", equipments, "skills", skills, "titles", titles));
    }

    // ─── 7.6. 进度选项列表 ───

    @GetMapping("/progress-options")
    public Result<Map<String, Object>> getProgressOptions(HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        // main chapters from main_chapters table
        List<Map<String, Object>> mainChapters = mainChapterMapper.selectList(null).stream().map(mc -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", mc.getChapterKey());
            m.put("name", mc.getName() != null ? mc.getName() : mc.getChapterKey());
            return m;
        }).collect(Collectors.toList());

        // locations from locations table
        List<Map<String, Object>> locations = locationMapper.selectList(null).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", l.getLocationKey());
            m.put("name", l.getName() != null ? l.getName() : l.getLocationKey());
            return m;
        }).collect(Collectors.toList());

        // distinct current_chapter values from players table
        QueryWrapper<Player> cq = new QueryWrapper<>();
        cq.select("DISTINCT current_chapter").isNotNull("current_chapter").ne("current_chapter", "").orderByAsc("current_chapter");
        List<String> chapters = playerMapper.selectList(cq).stream()
                .map(Player::getCurrentChapter).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // hardcoded stages
        List<Map<String, String>> stages = List.of(
                Map.of("key", "stage_1", "name", "第一阶段 — 初入星流"),
                Map.of("key", "stage_2", "name", "第二阶段 — 剧本觉醒"),
                Map.of("key", "stage_3", "name", "第三阶段 — 星辰试炼"),
                Map.of("key", "stage_4", "name", "第四阶段 — 终章前夜"),
                Map.of("key", "stage_5", "name", "第五阶段 — 结局降临")
        );

        return Result.ok(Map.of(
                "mainChapters", mainChapters,
                "locations", locations,
                "chapters", chapters,
                "stages", stages
        ));
    }

    // ─── 8. 发放物品/装备/技能/称号 ───

    @PostMapping("/players/{id}/grant")
    public Result<Map<String, Object>> grant(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        String type = (String) body.get("type");
        String key = (String) body.get("key");
        if (type == null || key == null) {
            return Result.fail("MISSING_PARAMS", "缺少 type 或 key 参数");
        }
        if (!List.of("item", "equipment", "skill", "title").contains(type)) {
            return Result.fail("INVALID_TYPE", "type 必须为 item/equipment/skill/title");
        }

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        switch (type) {
            case "item" -> {
                Item item = itemMapper.selectOne(new QueryWrapper<Item>().eq("item_key", key));
                if (item == null) return Result.fail("ITEM_NOT_FOUND", "道具 " + key + " 不存在");
                int qty = Math.max(1, toInt(body.get("quantity"), 1));
                PlayerInventory existing = playerInventoryMapper.selectOne(
                        new QueryWrapper<PlayerInventory>().eq("player_id", id).eq("item_key", key));
                if (existing != null) {
                    existing.setQuantity(existing.getQuantity() + qty);
                    playerInventoryMapper.updateById(existing);
                } else {
                    PlayerInventory inv = new PlayerInventory();
                    inv.setPlayerId(id);
                    inv.setItemKey(key);
                    inv.setQuantity(qty);
                    playerInventoryMapper.insert(inv);
                }
                addPlayerLog(id, "[管理员] 发放道具: " + (item.getName() != null ? item.getName() : key) + " x" + qty);
                logAction(admin, "grant_item", id, Map.of("item_key", key, "quantity", qty));
                pushStatsUpdated(id);
                return Result.ok(Map.of("type", type, "key", key, "quantity", qty,
                        "message", "已发放 " + (item.getName() != null ? item.getName() : key) + " x" + qty));
            }
            case "equipment" -> {
                Equipment eq = equipmentMapper.selectOne(new QueryWrapper<Equipment>().eq("equipment_key", key));
                if (eq == null) return Result.fail("EQUIP_NOT_FOUND", "装备 " + key + " 不存在");
                PlayerEquipment existing = playerEquipmentMapper.selectOne(
                        new QueryWrapper<PlayerEquipment>().eq("player_id", id).eq("equipment_key", key));
                if (existing != null) return Result.fail("ALREADY_HAS_EQUIP", "玩家已拥有该装备");
                PlayerEquipment pe = new PlayerEquipment();
                pe.setPlayerId(id);
                pe.setEquipmentKey(key);
                pe.setSlot(eq.getSlot() != null ? eq.getSlot() : "misc");
                pe.setEquipped(0);
                pe.setDurability(eq.getMaxDurability() != null ? eq.getMaxDurability() : 100);
                pe.setEnhancementLevel(0);
                pe.setAcquiredAt(LocalDateTime.now());
                playerEquipmentMapper.insert(pe);
                addPlayerLog(id, "[管理员] 发放装备: " + (eq.getName() != null ? eq.getName() : key));
                logAction(admin, "grant_equipment", id, Map.of("equipment_key", key));
                pushStatsUpdated(id);
                return Result.ok(Map.of("type", type, "key", key,
                        "message", "已发放 " + (eq.getName() != null ? eq.getName() : key)));
            }
            case "skill" -> {
                Skill skill = skillMapper.selectOne(new QueryWrapper<Skill>().eq("skill_key", key));
                if (skill == null) return Result.fail("SKILL_NOT_FOUND", "技能 " + key + " 不存在");
                PlayerSkill existing = playerSkillMapper.selectOne(
                        new QueryWrapper<PlayerSkill>().eq("player_id", id).eq("skill_key", key));
                if (existing != null) return Result.fail("ALREADY_HAS_SKILL", "玩家已拥有该技能");
                PlayerSkill ps = new PlayerSkill();
                ps.setPlayerId(id);
                ps.setSkillKey(key);
                ps.setLevel(1);
                ps.setEquipped(0);
                ps.setUnlockedAt(LocalDateTime.now());
                playerSkillMapper.insert(ps);
                addPlayerLog(id, "[管理员] 发放技能: " + (skill.getName() != null ? skill.getName() : key));
                logAction(admin, "grant_skill", id, Map.of("skill_key", key));
                pushStatsUpdated(id);
                return Result.ok(Map.of("type", type, "key", key,
                        "message", "已发放 " + (skill.getName() != null ? skill.getName() : key)));
            }
            case "title" -> {
                Title title = titleMapper.selectOne(new QueryWrapper<Title>().eq("title_key", key));
                if (title == null) return Result.fail("TITLE_NOT_FOUND", "称号 " + key + " 不存在");
                List<String> playerTitles = parseJsonList(p.getTitlesJson());
                if (playerTitles.contains(key)) return Result.fail("ALREADY_HAS_TITLE", "玩家已拥有该称号");
                playerTitles.add(key);
                try { p.setTitlesJson(objectMapper.writeValueAsString(playerTitles)); } catch (Exception ignored) {}
                playerMapper.updateById(p);
                addPlayerLog(id, "[管理员] 授予称号: " + (title.getName() != null ? title.getName() : key));
                logAction(admin, "grant_title", id, Map.of("title_key", key));
                pushStatsUpdated(id);
                return Result.ok(Map.of("type", type, "key", key,
                        "message", "已授予 " + (title.getName() != null ? title.getName() : key)));
            }
        }
        return Result.fail("UNKNOWN_TYPE", "未知类型");
    }

    // ─── 9. 快捷操作 ───

    @PostMapping("/players/{id}/quick-action")
    public Result<Map<String, Object>> quickAction(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        String action = (String) body.get("action");
        if (action == null) return Result.fail("MISSING_PARAMS", "缺少 action 参数");

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        Map<String, Object> stats = parseJsonMap(p.getStatsJson());
        String msg = "";

        switch (action) {
            case "fill_stamina" -> {
                stats.put("stamina", toInt(stats.get("maxStamina"), 50));
                msg = "体力已拉满";
            }
            case "fill_hp" -> {
                stats.put("hp", toInt(stats.get("maxHp"), 100));
                msg = "生命已拉满";
            }
            case "fill_all" -> {
                stats.put("stamina", toInt(stats.get("maxStamina"), 50));
                stats.put("hp", toInt(stats.get("maxHp"), 100));
                msg = "体力生命已拉满";
            }
            case "zero_stamina" -> {
                stats.put("stamina", 0);
                msg = "体力已清零";
            }
            case "start_rest" -> {
                stats.put("isResting", true);
                msg = "已进入休息状态";
            }
            case "stop_rest" -> {
                stats.put("isResting", false);
                msg = "已停止休息";
            }
            case "clear_death" -> {
                stats.put("isDead", false);
                stats.put("hp", Math.max(toInt(stats.get("hp"), 1), 1));
                msg = "已清除死亡状态";
            }
            case "kill_player" -> {
                stats.put("isDead", true);
                stats.put("hp", 0);
                msg = "已将玩家设为死亡";
            }
            case "fill_rank_requirements" -> {
                return fillRankRequirements(id, admin);
            }
            case "force_rank_up" -> {
                return forceRankUp(id, p.getUserId(), admin);
            }
            default -> {
                return Result.fail("INVALID_ACTION", "无效的操作: " + action);
            }
        }

        try { p.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
        playerMapper.updateById(p);
        addPlayerLog(id, "[管理员] " + msg);
        logAction(admin, "quick_action", id, Map.of("action", action, "result", msg));
        pushStatsUpdated(id);

        return Result.ok(Map.of("player", buildPlayerFull(p), "message", msg));
    }

    private Result<Map<String, Object>> fillRankRequirements(Long playerId, String admin) {
        try {
            var info = avatarRankService.getAvatarRankInfo(playerId);
            if (info.isMaxRank()) return Result.fail("MAX_RANK", "已达最高位阶，无法填充条件");

            Player p = playerMapper.selectById(playerId);
            Map<String, Object> stats = parseJsonMap(p.getStatsJson());
            Map<String, Object> stageProgress = parseJsonMap(p.getStageProgressJson());

            var reqs = info.getRequirements();
            if (reqs != null) {
                for (var req : reqs) {
                    switch (req.getType()) {
                        case "levelMin" -> stats.put("level", Math.max(toInt(stats.get("level"), 1), req.getRequired()));
                        case "storyFragmentsMin" -> p.setStoryFragments(Math.max(
                                p.getStoryFragments() != null ? p.getStoryFragments() : 0, req.getRequired()));
                        case "channelHeatMin" -> stats.put("channelHeat", Math.max(
                                toInt(stats.get("channelHeat"), 0), req.getRequired()));
                        case "titlesCountMin" -> {
                            long count = playerTitleMapper.selectCount(
                                    new QueryWrapper<com.huazhenghai.readergame.entity.PlayerTitle>()
                                            .eq("player_id", playerId));
                            String[] pool = {"first_explorer", "station_witness", "story_fragment_collector",
                                    "starstream_observed", "boss_trace_hunter"};
                            int pi = 0;
                            while (count < req.getRequired() && pi < pool.length) {
                                var exists = playerTitleMapper.selectOne(
                                        new QueryWrapper<com.huazhenghai.readergame.entity.PlayerTitle>()
                                                .eq("player_id", playerId).eq("title_key", pool[pi]));
                                if (exists == null) {
                                    var pt = new com.huazhenghai.readergame.entity.PlayerTitle();
                                    pt.setPlayerId(playerId);
                                    pt.setTitleKey(pool[pi]);
                                    pt.setUnlockedAt(java.time.LocalDateTime.now());
                                    playerTitleMapper.insert(pt);
                                    count++;
                                }
                                pi++;
                            }
                        }
                        case "worldLineShiftMin" -> stats.put("worldLineShift", Math.max(
                                toInt(stats.get("worldLineShift"), 0), req.getRequired()));
                        case "explorationsByLocation" -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> byLoc = (Map<String, Object>)
                                    stageProgress.computeIfAbsent("explorationsByLocation", k -> new LinkedHashMap<>());
                            String locKey = req.getReqKey() != null ? req.getReqKey() : "any";
                            byLoc.put(locKey, Math.max(toInt(byLoc.get(locKey), 0), req.getRequired()));
                        }
                        case "storyEventsTriggeredMin" -> {
                            @SuppressWarnings("unchecked")
                            List<String> triggered = (List<String>)
                                    stageProgress.computeIfAbsent("storyEventsTriggered", k -> new ArrayList<>());
                            while (triggered.size() < req.getRequired()) {
                                triggered.add("admin_placeholder_event_" + triggered.size());
                            }
                        }
                        case "bossClue" -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> clues = (Map<String, Object>)
                                    stageProgress.computeIfAbsent("bossClues", k -> new LinkedHashMap<>());
                            String bossKey = req.getReqKey() != null ? req.getReqKey() : "unknown_boss";
                            clues.put(bossKey, Math.max(toInt(clues.get(bossKey), 0), req.getRequired()));
                        }
                        case "pkRatingMin" -> stats.put("rating", Math.max(
                                toInt(stats.get("rating"), 1000), req.getRequired()));
                        case "rareTitleRequired" -> {
                            var exists = playerTitleMapper.selectOne(
                                    new QueryWrapper<com.huazhenghai.readergame.entity.PlayerTitle>()
                                            .eq("player_id", playerId).eq("title_key", "boss_trace_hunter"));
                            if (exists == null) {
                                var pt = new com.huazhenghai.readergame.entity.PlayerTitle();
                                pt.setPlayerId(playerId);
                                pt.setTitleKey("boss_trace_hunter");
                                pt.setUnlockedAt(java.time.LocalDateTime.now());
                                playerTitleMapper.insert(pt);
                            }
                        }
                    }
                }
            }

            try { p.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
            try { p.setStageProgressJson(objectMapper.writeValueAsString(stageProgress)); } catch (Exception ignored) {}
            playerMapper.updateById(p);
            addPlayerLog(playerId, "[管理员] 位阶条件已一键满足");
            logAction(admin, "fill_rank_reqs", playerId, Map.of());

            return Result.ok(Map.of("player", buildPlayerFull(p), "message", "位阶条件已一键满足"));
        } catch (Exception e) {
            return Result.fail("SERVER_ERROR", e.getMessage());
        }
    }

    private Result<Map<String, Object>> forceRankUp(Long playerId, Long userId, String admin) {
        try {
            var result = avatarRankService.rankUp(playerId, userId);
            addPlayerLog(playerId, "[管理员] 已执行升阶: " + result.getCurrentDisplayName());
            logAction(admin, "force_rank_up", playerId, Map.of("newRank", result.getCurrentRank()));
            return Result.ok(Map.of("player", buildPlayerFull(playerMapper.selectById(playerId)),
                    "message", "已执行升阶: " + result.getCurrentDisplayName()));
        } catch (Exception e) {
            return Result.fail("RANK_UP_FAILED", e.getMessage());
        }
    }

    // ─── 10. 玩家日志 ───

    @GetMapping("/players/{id}/logs")
    public Result<Map<String, Object>> getPlayerLogs(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "50") int limit,
                                                      HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        QueryWrapper<PlayerLog> query = new QueryWrapper<PlayerLog>()
                .eq("player_id", id).orderByDesc("id").last("LIMIT " + limit);
        List<PlayerLog> logs = playerLogMapper.selectList(query);
        Long total = playerLogMapper.selectCount(new QueryWrapper<PlayerLog>().eq("player_id", id));

        List<String> messages = new ArrayList<>();
        for (PlayerLog pl : logs) {
            messages.add(pl.getMessage());
        }

        return Result.ok(Map.of("logs", messages, "total", total));
    }

    // ─── 11. 反馈管理 ───

    @GetMapping("/feedback")
    public Result<Map<String, Object>> getFeedback(@RequestParam(required = false) String status,
                                                    HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        List<Feedback> items = feedbackService.getAll(status);
        return Result.ok(Map.of("feedback", items, "total", items.size()));
    }

    @PatchMapping("/feedback/{id}")
    public Result<Map<String, Object>> updateFeedback(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body,
                                                       HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        String status = (String) body.get("status");
        String note = (String) body.get("note");
        Feedback updated = feedbackService.update(id, status, note);
        if (updated == null) return Result.fail("FEEDBACK_NOT_FOUND", "反馈不存在");

        logAction(admin, "update_feedback", null, Map.of("feedbackId", id, "status", status, "note", note));
        return Result.ok(Map.of("feedback", updated));
    }

    // ─── 12. 管理员操作日志 ───

    @GetMapping("/actions")
    public Result<Map<String, Object>> getActions(@RequestParam(defaultValue = "50") int limit,
                                                   HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        QueryWrapper<AdminActionLog> query = new QueryWrapper<AdminActionLog>()
                .orderByDesc("id").last("LIMIT " + limit);
        List<AdminActionLog> actions = adminActionLogMapper.selectList(query);
        return Result.ok(Map.of("actions", actions, "total", actions.size()));
    }

    // ─── 13. 称号管理 ───

    @GetMapping("/titles")
    public Result<Map<String, Object>> getTitles(HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        List<Title> titles = titleMapper.selectList(new QueryWrapper<Title>().orderByAsc("id"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Title t : titles) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("title_key", t.getTitleKey());
            item.put("name", t.getName());
            item.put("description", t.getDescription());
            item.put("rarity", t.getRarity());
            item.put("category", t.getCategory());
            item.put("unlock_conditions_json", t.getUnlockConditionsJson());
            item.put("effects_json", t.getEffectsJson());
            item.put("tags_json", t.getTagsJson());
            result.add(item);
        }
        return Result.ok(Map.of("titles", result));
    }

    // ─── 14. 玩家称号列表 ───

    @GetMapping("/players/{id}/titles")
    public Result<Map<String, Object>> getPlayerTitles(@PathVariable Long id, HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        List<String> playerTitles = parseJsonList(p.getTitlesJson());
        List<Title> allTitles = titleMapper.selectList(new QueryWrapper<>());
        Map<String, Title> titleMap = new LinkedHashMap<>();
        for (Title t : allTitles) titleMap.put(t.getTitleKey(), t);

        List<Map<String, Object>> result = new ArrayList<>();
        for (String tk : playerTitles) {
            Title t = titleMap.get(tk);
            result.add(Map.of("title_key", tk,
                    "name", t != null ? t.getName() : tk,
                    "rarity", t != null ? t.getRarity() : "common"));
        }
        return Result.ok(Map.of("titles", result));
    }

    @PostMapping("/players/{id}/titles")
    public Result<Map<String, Object>> modifyPlayerTitle(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        String action = (String) body.get("action");
        String titleKey = (String) body.get("title_key");
        if (action == null || titleKey == null)
            return Result.fail("MISSING_PARAMS", "缺少必要参数");
        if (!List.of("grant", "revoke").contains(action))
            return Result.fail("INVALID_ACTION", "action 必须为 grant 或 revoke");

        Player p = playerMapper.selectById(id);
        if (p == null) return Result.fail("PLAYER_NOT_FOUND", "玩家不存在");

        Title title = titleMapper.selectOne(new QueryWrapper<Title>().eq("title_key", titleKey));
        if (title == null) return Result.fail("TITLE_NOT_FOUND", "称号不存在");

        List<String> playerTitles = parseJsonList(p.getTitlesJson());
        if ("grant".equals(action)) {
            if (playerTitles.contains(titleKey))
                return Result.fail("ALREADY_HAS_TITLE", "玩家已拥有该称号");
            playerTitles.add(titleKey);
            addPlayerLog(id, "[管理员] 授予称号: " + title.getName());
            logAction(admin, "grant_title", id, Map.of("title_key", titleKey));
        } else {
            if (!playerTitles.contains(titleKey))
                return Result.fail("NOT_HAVE_TITLE", "玩家未拥有该称号");
            playerTitles.remove(titleKey);
            addPlayerLog(id, "[管理员] 移除称号: " + title.getName());
            logAction(admin, "revoke_title", id, Map.of("title_key", titleKey));
        }

        try { p.setTitlesJson(objectMapper.writeValueAsString(playerTitles)); } catch (Exception ignored) {}
        playerMapper.updateById(p);
        return Result.ok(Map.of("titles", playerTitles));
    }

    // ─── 辅助方法 ───

    private Map<String, Object> buildPlayerFull(Player p) {
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("id", p.getId());
        player.put("player_name", p.getPlayerName());
        player.put("user_id", p.getUserId());
        player.put("coins", p.getCoins());
        player.put("story_fragments", p.getStoryFragments());
        player.put("constellationFavor", p.getConstellationFavor());
        player.put("abyssMark", p.getAbyssMark());
        player.put("current_main_chapter", p.getCurrentMainChapter());
        player.put("current_chapter", p.getCurrentChapter());
        player.put("current_location", p.getCurrentLocation());
        player.put("current_stage", p.getCurrentStage());
        player.put("titles_json", p.getTitlesJson());
        player.put("stats_json", p.getStatsJson());
        // Parsed stats for frontend edit form
        Map<String, Object> parsedStats = parseJsonMap(p.getStatsJson());
        // 确保 maxExp 始终根据 level 计算
        int lvl = parsedStats.get("level") instanceof Number n ? n.intValue() : 1;
        parsedStats.putIfAbsent("maxExp", 100 * lvl * lvl);
        player.put("stats", parsedStats);
        player.put("level", toInt(parsedStats.get("level"), 1));
        player.put("hp", toInt(parsedStats.get("hp"), 100));
        player.put("maxHp", toInt(parsedStats.get("maxHp"), 100));
        player.put("stamina", toInt(parsedStats.get("stamina"), 50));
        player.put("maxStamina", toInt(parsedStats.get("maxStamina"), 50));
        player.put("attack", toInt(parsedStats.get("attack"), 10));
        player.put("defense", toInt(parsedStats.get("defense"), 5));
        player.put("speed", toInt(parsedStats.get("speed"), 10));
        player.put("isDead", Boolean.TRUE.equals(parsedStats.get("isDead")));
        player.put("isResting", Boolean.TRUE.equals(parsedStats.get("isResting")));
        player.put("avatarRank", parsedStats.getOrDefault("avatarRank", "F"));
        player.put("avatarRankName", parsedStats.getOrDefault("avatarRankName", "临时化身"));
        player.put("storyGrade", parsedStats.getOrDefault("storyGrade", "ordinary"));
        player.put("story_flags_json", p.getStoryFlagsJson());
        player.put("permanent_flags_json", p.getPermanentFlagsJson());
        player.put("stage_progress_json", p.getStageProgressJson());
        player.put("stageProgress", parseJsonMap(p.getStageProgressJson()));
        player.put("created_at", p.getCreatedAt());
        player.put("updated_at", p.getUpdatedAt());
        // 装备属性加成
        try {
            Map<String, Object> equipBonus = equipmentService.calculateEquipmentBonus(p.getId());
            player.put("equipmentBonus", equipBonus != null ? equipBonus : Collections.emptyMap());
        } catch (Exception e) {
            player.put("equipmentBonus", Collections.emptyMap());
        }
        return player;
    }

    private String rankName(String rank) {
        return switch (rank != null ? rank : "F") {
            case "F" -> "临时化身"; case "E" -> "剧本幸存者"; case "D" -> "频道记录者";
            case "C" -> "剧本执行者"; case "B" -> "星流候选者"; case "A" -> "故事承载者";
            case "S" -> "终章注视者"; case "SS" -> "故事之王"; case "SSS" -> "全知读者";
            default -> "临时化身";
        };
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return new LinkedHashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return new ArrayList<>();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    private double toDouble(Object val, double def) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return def;
    }

    private Number toNumber(Object val, Number def) {
        if (val instanceof Number) return (Number) val;
        return def;
    }

    private Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }
}
