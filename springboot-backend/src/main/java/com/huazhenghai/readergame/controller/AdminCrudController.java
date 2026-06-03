package com.huazhenghai.readergame.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理后台通用 CRUD 控制器.
 * 为 12 组实体提供 list/get/create/update/delete 端点.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCrudController {

    private static final Logger log = LoggerFactory.getLogger(AdminCrudController.class);

    @Value("${admin.key:}")
    private String adminKey;

    private final ObjectMapper objectMapper;
    private final Map<String, CrudConfig> registry = new LinkedHashMap<>();

    // ── Mapper 注入 ──
    private final ExplorationEventMapper explorationEventMapper;
    private final NpcGhostMapper npcGhostMapper;
    private final BroadcastEventMapper broadcastEventMapper;
    private final ConstellationFactionMapper constellationFactionMapper;
    private final ItemMapper itemMapper;
    private final EquipmentMapper equipmentMapper;
    private final SkillMapper skillMapper;
    private final MonsterMapper monsterMapper;
    private final TitleMapper titleMapper;
    private final LocationMapper locationMapper;
    private final QuestMapper questMapper;
    private final WorldBossMapper worldBossMapper;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public AdminCrudController(
            ExplorationEventMapper explorationEventMapper,
            NpcGhostMapper npcGhostMapper,
            BroadcastEventMapper broadcastEventMapper,
            ConstellationFactionMapper constellationFactionMapper,
            ItemMapper itemMapper,
            EquipmentMapper equipmentMapper,
            SkillMapper skillMapper,
            MonsterMapper monsterMapper,
            TitleMapper titleMapper,
            LocationMapper locationMapper,
            QuestMapper questMapper,
            WorldBossMapper worldBossMapper,
            ObjectMapper objectMapper) {
        this.explorationEventMapper = explorationEventMapper;
        this.npcGhostMapper = npcGhostMapper;
        this.broadcastEventMapper = broadcastEventMapper;
        this.constellationFactionMapper = constellationFactionMapper;
        this.itemMapper = itemMapper;
        this.equipmentMapper = equipmentMapper;
        this.skillMapper = skillMapper;
        this.monsterMapper = monsterMapper;
        this.titleMapper = titleMapper;
        this.locationMapper = locationMapper;
        this.questMapper = questMapper;
        this.worldBossMapper = worldBossMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initRegistry() {
        registry.put("exploration-events", new CrudConfig(explorationEventMapper, ExplorationEvent.class, "event_key"));
        registry.put("npc-ghosts", new CrudConfig(npcGhostMapper, NpcGhost.class, "ghost_key"));
        registry.put("broadcast-events", new CrudConfig(broadcastEventMapper, BroadcastEvent.class, "event_key"));
        registry.put("constellation-factions", new CrudConfig(constellationFactionMapper, ConstellationFaction.class, "faction_key"));
        registry.put("items", new CrudConfig(itemMapper, Item.class, "item_key"));
        registry.put("equipments", new CrudConfig(equipmentMapper, Equipment.class, "equipment_key"));
        registry.put("skills", new CrudConfig(skillMapper, Skill.class, "skill_key"));
        registry.put("monsters", new CrudConfig(monsterMapper, Monster.class, "monster_key"));
        registry.put("titles", new CrudConfig(titleMapper, Title.class, "title_key"));
        registry.put("locations", new CrudConfig(locationMapper, Location.class, "location_key"));
        registry.put("quests", new CrudConfig(questMapper, Quest.class, "quest_key"));
        registry.put("world-bosses", new CrudConfig(worldBossMapper, WorldBoss.class, "boss_key"));
    }

    // ── 认证 ──

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

    private CrudConfig requireConfig(String entity) {
        CrudConfig config = registry.get(entity);
        if (config == null) {
            throw new IllegalArgumentException("未知实体类型: " + entity + "，可用: " + String.join(", ", registry.keySet()));
        }
        return config;
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. 列表 (支持搜索和分页)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/crud/{entity}")
    public Result<Map<String, Object>> list(
            @PathVariable String entity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        CrudConfig config = requireConfig(entity);
        return listEntities(config, search, limit, offset);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Result<Map<String, Object>> listEntities(CrudConfig config, String search, int limit, int offset) {
        QueryWrapper<?> query = new QueryWrapper<>();
        if (search != null && !search.isBlank()) {
            String safe = search.replace("'", "''").replace("\\", "\\\\");
            query.and(w -> w.apply("name LIKE '%" + safe + "%'")
                    .or().apply(config.keyField + " LIKE '%" + safe + "%'"));
        }
        query.orderByDesc("id").last("LIMIT " + offset + "," + limit);

        List<?> rows = config.mapper.selectList(query);
        Long total = config.mapper.selectCount(new QueryWrapper<>());

        return Result.ok(Map.of("rows", rows, "total", total));
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. 详情
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/crud/{entity}/{id}")
    public Result<Map<String, Object>> getById(
            @PathVariable String entity,
            @PathVariable Long id,
            HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        CrudConfig config = requireConfig(entity);
        Object row = config.mapper.selectById(id);
        if (row == null) return Result.fail("NOT_FOUND", "记录不存在");

        return Result.ok(Map.of("row", row));
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. 创建
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/crud/{entity}")
    public Result<Map<String, Object>> create(
            @PathVariable String entity,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        try {
            CrudConfig config = requireConfig(entity);
            Object row = objectMapper.convertValue(body, config.entityClass);
            trySetTimestamps(row, true);
            config.mapper.insert(row);
            log.info("Admin [{}] 创建 {} id={}", admin, entity, extractId(row));
            return Result.ok(Map.of("row", row));
        } catch (Exception e) {
            log.error("创建 {} 失败", entity, e);
            return Result.fail("CREATE_FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. 更新
    // ═══════════════════════════════════════════════════════════════

    @PutMapping("/crud/{entity}/{id}")
    public Result<Map<String, Object>> update(
            @PathVariable String entity,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        try {
            CrudConfig config = requireConfig(entity);
            Object existing = config.mapper.selectById(id);
            if (existing == null) return Result.fail("NOT_FOUND", "记录不存在");

            // 合并 body 到现有实体（避免 updateById 把未传字段覆盖为 null）
            Object merged = objectMapper.updateValue(existing, body);
            trySetTimestamps(merged, false);
            config.mapper.updateById(merged);
            log.info("Admin [{}] 更新 {} id={}", admin, entity, id);
            return Result.ok(Map.of("row", merged));
        } catch (Exception e) {
            log.error("更新 {} id={} 失败", entity, id, e);
            return Result.fail("UPDATE_FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. 删除
    // ═══════════════════════════════════════════════════════════════

    @DeleteMapping("/crud/{entity}/{id}")
    public Result<Map<String, Object>> delete(
            @PathVariable String entity,
            @PathVariable Long id,
            HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        CrudConfig config = requireConfig(entity);
        Object existing = config.mapper.selectById(id);
        if (existing == null) return Result.fail("NOT_FOUND", "记录不存在");

        config.mapper.deleteById(id);
        log.info("Admin [{}] 删除 {} id={}", admin, entity, id);
        return Result.ok(Map.of("deleted", id));
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. 实体列表 — 供管理前端下拉选择
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/crud-entities")
    public Result<List<Map<String, String>>> listEntities(HttpServletRequest request) {
        String admin = checkAdmin(request);
        if (admin == null) return forbidden();

        List<Map<String, String>> result = new ArrayList<>();
        for (var entry : registry.entrySet()) {
            result.add(Map.of("key", entry.getKey(), "label", toLabel(entry.getKey())));
        }
        return Result.ok(result);
    }

    // ── 辅助方法 ──

    private String toLabel(String key) {
        return switch (key) {
            case "exploration-events" -> "探索事件";
            case "npc-ghosts" -> "NPC鬼怪";
            case "broadcast-events" -> "星流广播";
            case "constellation-factions" -> "星座势力";
            case "items" -> "道具";
            case "equipments" -> "装备";
            case "skills" -> "技能";
            case "monsters" -> "怪物";
            case "titles" -> "称号";
            case "locations" -> "地点";
            case "quests" -> "任务";
            case "world-bosses" -> "世界Boss";
            default -> key;
        };
    }

    private void trySetTimestamps(Object row, boolean isCreate) {
        try {
            var cls = row.getClass();
            if (isCreate) {
                try {
                    var m = cls.getMethod("setCreatedAt", LocalDateTime.class);
                    m.invoke(row, LocalDateTime.now());
                } catch (NoSuchMethodException ignored) {}
            }
            try {
                var m = cls.getMethod("setUpdatedAt", LocalDateTime.class);
                m.invoke(row, LocalDateTime.now());
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            log.warn("设置时间戳失败: {}", e.getMessage());
        }
    }

    private Long extractId(Object row) {
        try {
            return (Long) row.getClass().getMethod("getId").invoke(row);
        } catch (Exception e) {
            return null;
        }
    }

    private void setId(Object row, Long id) {
        try {
            row.getClass().getMethod("setId", Long.class).invoke(row, id);
        } catch (Exception ignored) {}
    }

    // ── 内部配置类 ──

    @SuppressWarnings("rawtypes")
    private static class CrudConfig {
        final BaseMapper mapper;
        final Class<?> entityClass;
        final String keyField;

        CrudConfig(BaseMapper<?> mapper, Class<?> entityClass, String keyField) {
            this.mapper = mapper;
            this.entityClass = entityClass;
            this.keyField = keyField;
        }
    }
}
