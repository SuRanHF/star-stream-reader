package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.vo.LogEntry;
import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.PlayerService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 玩家角色服务实现.
 * <p>
 * 负责创建玩家、读取玩家信息、JSON 字段解析等.
 * </p>
 */
@Service
public class PlayerServiceImpl implements PlayerService {

    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;

    public PlayerServiceImpl(PlayerMapper playerMapper,
                             PlayerLogMapper playerLogMapper,
                             LocationMapper locationMapper,
                             ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.locationMapper = locationMapper;
        this.objectMapper = objectMapper;
    }

    // ── 默认属性 JSON (与 Node.js defaultStats 保持一致) ──

    private static final String DEFAULT_STATS_JSON =
        "{" +
        "\"level\":1,\"exp\":0,\"maxExp\":100,\"hp\":100,\"maxHp\":100," +
        "\"attack\":10,\"defense\":5,\"speed\":3," +
        "\"critRate\":0.0,\"critDamage\":1.5," +
        "\"stamina\":50,\"maxStamina\":50," +
        "\"explorationPower\":1,\"luck\":1,\"dropRate\":0," +
        "\"rating\":1000,\"pkWins\":0,\"pkLosses\":0,\"pkStreak\":0," +
        "\"worldLineShift\":0,\"channelHeat\":0," +
        "\"freePoints\":10," +
        "\"allocatedAtk\":0,\"allocatedDef\":0,\"allocatedSpd\":0,\"allocatedCrit\":0," +
        "\"constellation\":null," +
        "\"avatarRank\":\"F\",\"avatarRankName\":\"临时化身\",\"storyGrade\":\"ordinary\"," +
        "\"isResting\":false,\"restStartedAt\":null,\"lastRecoveryAt\":null," +
        "\"intelligence\":0,\"combat\":0,\"leadership\":0,\"bond\":0,\"cruelty\":0,\"insight\":0" +
        "}";

    private static final String DEFAULT_STAGE_PROGRESS_JSON =
        "{" +
        "\"storyEventsTriggered\":[],\"sideEventsTriggered\":[]," +
        "\"bossClues\":{}," +
        "\"opportunityEventsTriggered\":[],\"hiddenEventsTriggered\":[]," +
        "\"storyPity\":0,\"explorationsByLocation\":{}," +
        "\"finalStoryEventTriggered\":null,\"lastExplorationResultType\":null" +
        "}";

    /**
     * 创建玩家角色.
     * <ol>
     *   <li>检查该用户是否已有角色 (一人一角色)</li>
     *   <li>填充默认 stats / stageProgress JSON</li>
     *   <li>写入数据库</li>
     *   <li>记录初始日志</li>
     * </ol>
     */
    @Override
    public PlayerVO create(String playerName, Long userId) {
        // 一人一角色检查
        Player existing = findByUserId(userId);
        if (existing != null) {
            throw new com.huazhenghai.readergame.common.BusinessException(
                    ErrorCode.ALREADY_HAS_PLAYER, "该账号已绑定角色，不可重复创建");
        }

        String name = (playerName != null && !playerName.isBlank()) ? playerName.trim() : "未命名读者";

        Player player = new Player();
        player.setPlayerName(name);
        player.setCurrentChapter("ch1_01_last_train");
        player.setCoins(0);
        player.setStoryFragments(0);
        player.setUserId(userId);
        player.setCurrentMainChapter("main_ch01_paid_service");
        player.setCurrentLocation("");
        player.setStatsJson(DEFAULT_STATS_JSON);
        player.setStageProgressJson(DEFAULT_STAGE_PROGRESS_JSON);
        player.setHelpDate("");
        player.setDailyHelpCount(0);
        player.setDailyAssistCount(0);

        playerMapper.insert(player);

        // 写入初始日志
        writeLog(player.getId(), "info", "化身档案已创建，星流开始记录你的行动。");

        return buildPlayerVO(player, Collections.emptyList());
    }

    /**
     * 根据玩家 ID 获取完整 PlayerVO.
     */
    @Override
    public PlayerVO getPlayer(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            return null;
        }
        List<LogEntry> recentLogs = getRecentLogs(playerId, 50);
        return buildPlayerVO(player, recentLogs);
    }

    /**
     * 根据玩家 ID 获取原始实体 (Service 内部使用).
     */
    @Override
    public Player getPlayerEntity(Long playerId) {
        return playerMapper.selectById(playerId);
    }

    /**
     * 根据用户 ID 查找玩家.
     */
    @Override
    public Player findByUserId(Long userId) {
        QueryWrapper<Player> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        return playerMapper.selectOne(query);
    }

    // ── 内部方法 ──

    /**
     * 将 Player 实体构建为 PlayerVO.
     *
     * @param player     玩家实体
     * @param recentLogs 最近日志列表
     * @return 解析后的 PlayerVO
     */
    private PlayerVO buildPlayerVO(Player player, List<LogEntry> recentLogs) {
        // 安全解析 JSON 字段
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());

        // maxExp 始终根据 level 实时计算: maxExp = 100 * level^2
        int level = stats.get("level") instanceof Number n ? n.intValue() : 1;
        stats.put("maxExp", 100 * level * level);
        // 修复异常属性: 确保不低于基准值 (防御负数/坏数据)
        int curAttack = stats.get("attack") instanceof Number n ? n.intValue() : 10;
        int curDefense = stats.get("defense") instanceof Number n ? n.intValue() : 5;
        int curSpeed = stats.get("speed") instanceof Number n ? n.intValue() : 3;
        double curCritRate = stats.get("critRate") instanceof Number n ? n.doubleValue() : 0.0;
        if (curAttack < 10) stats.put("attack", 10);
        if (curDefense < 5) stats.put("defense", 5);
        if (curSpeed < 3) stats.put("speed", 3);
        if (curCritRate < 0.0) stats.put("critRate", 0.0);

        // ─── 属性来源拆解 → 前端 tooltip 用 ───
        int allocAtk  = stats.get("allocatedAtk") instanceof Number n ? n.intValue() : 0;
        int allocDef  = stats.get("allocatedDef") instanceof Number n ? n.intValue() : 0;
        int allocSpd  = stats.get("allocatedSpd") instanceof Number n ? n.intValue() : 0;
        int allocCrit = stats.get("allocatedCrit") instanceof Number n ? n.intValue() : 0;

        stats.put("bonusAtkBase", 10);
        stats.put("bonusAtkAlloc", allocAtk);
        stats.put("bonusAtkConstellation", 0);
        stats.put("bonusAtkEquipment", 0);
        stats.put("bonusAtkSkill", 0);
        stats.put("bonusAtkFaction", 0);
        stats.put("bonusDefBase", 5);
        stats.put("bonusDefAlloc", allocDef);
        stats.put("bonusDefConstellation", 0);
        stats.put("bonusDefEquipment", 0);
        stats.put("bonusDefSkill", 0);
        stats.put("bonusDefFaction", 0);
        stats.put("bonusSpdBase", 3);
        stats.put("bonusSpdAlloc", allocSpd);
        stats.put("bonusSpdConstellation", 0);
        stats.put("bonusSpdEquipment", 0);
        stats.put("bonusSpdSkill", 0);
        stats.put("bonusSpdFaction", 0);
        stats.put("bonusCritBase", 0.0);
        stats.put("bonusCritAlloc", allocCrit);
        stats.put("bonusCritConstellation", 0.0);
        stats.put("bonusCritEquipment", 0.0);
        stats.put("bonusCritSkill", 0.0);
        stats.put("bonusCritFaction", 0.0);
        stats.put("bonusMaxHpBase", 100);
        stats.put("bonusMaxHpAlloc", 0);
        stats.put("bonusMaxHpConstellation", 0);
        stats.put("bonusMaxHpEquipment", 0);
        stats.put("bonusMaxHpSkill", 0);
        stats.put("bonusMaxHpFaction", 0);

        // 背后星(星座)属性加成 → 仅写入 bonus 字段，不直接修改实际值
        String constellation = stats.get("constellation") instanceof String s ? s : null;
        int constAtk = 0, constDef = 0, constSpd = 0, constMaxHp = 0;
        double constCrit = 0.0;
        if (constellation != null && !constellation.isEmpty()) {
            String cname = CONSTELLATION_NAMES.getOrDefault(constellation, constellation);
            stats.put("bonusConstellationName", cname);
            Map<String, Object> effects = getConstellationEffects(constellation);
            if (effects != null) {
                for (Map.Entry<String, Object> e : effects.entrySet()) {
                    String key = e.getKey();
                    double bonus = e.getValue() instanceof Number n ? n.doubleValue() : 0;
                    if (bonus == 0) continue;
                    switch (key) {
                        case "atk":
                            constAtk = (int) bonus;
                            stats.put("bonusAtkConstellation", constAtk);
                            break;
                        case "def":
                            constDef = (int) bonus;
                            stats.put("bonusDefConstellation", constDef);
                            break;
                        case "spd":
                            constSpd = (int) bonus;
                            stats.put("bonusSpdConstellation", constSpd);
                            break;
                        case "maxHp":
                            constMaxHp = (int) bonus;
                            stats.put("bonusMaxHpConstellation", constMaxHp);
                            break;
                        case "critRate":
                            constCrit = bonus;
                            stats.put("bonusCritConstellation", constCrit);
                            break;
                        case "luck":
                            stats.put("luck", toInt(stats.get("luck"), 1) + (int) bonus);
                            break;
                        case "insight":
                            stats.put("insight", toInt(stats.get("insight"), 0) + (int) bonus);
                            break;
                        case "worldLineShift":
                            stats.put("worldLineShift", toInt(stats.get("worldLineShift"), 0) + (int) bonus);
                            break;
                    }
                }
            }
        } else {
            stats.put("bonusConstellationName", "");
        }
        stats.put("bonusFactionName", "");

        // ─── 从组件重新计算实际属性值 (base + alloc + constellation) ───
        // 装备/技能的加成在 GameBootstrapServiceImpl 中另行合并
        stats.put("attack", 10 + allocAtk + constAtk);
        stats.put("defense", 5 + allocDef + constDef);
        stats.put("speed", 3 + allocSpd + constSpd);
        stats.put("maxHp", 100 + constMaxHp);
        stats.put("critRate", allocCrit * 0.02 + constCrit);

        // 上限校验: 当前 HP 不能超过 maxHp
        int calcMaxHp = 100 + constMaxHp;
        if (stats.get("hp") instanceof Number hpNum && hpNum.intValue() > calcMaxHp) {
            stats.put("hp", calcMaxHp);
        }

        String currentLocationKey = player.getCurrentLocation();
        String currentLocationName = resolveLocationName(currentLocationKey);

        return PlayerVO.builder()
                .id(player.getId())
                .playerName(player.getPlayerName())
                .currentChapter(player.getCurrentChapter())
                .coins(player.getCoins())
                .storyFragments(player.getStoryFragments())
                .userId(player.getUserId())
                .stats(stats)
                .stageProgress(stageProgress)
                .currentMainChapter(player.getCurrentMainChapter())
                .currentLocation(currentLocationKey)
                .currentLocationName(currentLocationName)
                .recentLogs(recentLogs)
                .createdAt(player.getCreatedAt())
                .updatedAt(player.getUpdatedAt())
                .build();
    }

    /**
     * 安全地将 JSON 字符串解析为 Map.
     * 解析失败时返回空 Map.
     */
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 获取玩家最近的 N 条日志.
     */
    private List<LogEntry> getRecentLogs(Long playerId, int limit) {
        QueryWrapper<PlayerLog> query = new QueryWrapper<>();
        query.eq("player_id", playerId)
             .orderByDesc("created_at")
             .last("LIMIT " + limit);
        List<PlayerLog> logs = playerLogMapper.selectList(query);

        List<LogEntry> result = new ArrayList<>();
        for (PlayerLog log : logs) {
            result.add(new LogEntry(
                    log.getId(),
                    log.getMessage(),
                    log.getType(),
                    log.getCreatedAt()
            ));
        }
        // 按时间正序返回
        Collections.reverse(result);
        return result;
    }

    /**
     * 根据 location_key 解析为中文名称.
     */
    private String resolveLocationName(String locationKey) {
        if (locationKey == null || locationKey.isBlank()) {
            return "";
        }
        try {
            QueryWrapper<Location> query = new QueryWrapper<>();
            query.eq("location_key", locationKey);
            Location loc = locationMapper.selectOne(query);
            return loc != null ? loc.getName() : locationKey;
        } catch (Exception e) {
            return locationKey;
        }
    }

    /**
     * 写入一条玩家日志.
     *
     * @param playerId 玩家 ID
     * @param type     日志类型 (info / battle / exploration / milestone)
     * @param message  日志内容
     */
    public void writeLog(Long playerId, String type, String message) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        playerLogMapper.insert(log);
    }

    // ─── 工具方法 ───

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        return defaultVal;
    }

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        return defaultVal;
    }

    // ─── 背后星(星座)属性加成 ───
    // key → effects: atk, def, spd, maxHp, critRate, luck, insight, worldLineShift

    private static final Map<String, Map<String, Object>> CONSTELLATION_EFFECTS = Map.of(
        "demon_judge_of_fire",       Map.of("def", 15, "maxHp", 10),
        "master_of_steel",           Map.of("def", 8, "maxHp", 20),
        "prisoner_of_golden_headband", Map.of("spd", 18, "luck", 4),
        "abyssal_black_flame_dragon", Map.of("atk", 15, "critRate", 0.10),
        "queen_of_darkest_spring",   Map.of("atk", 12, "worldLineShift", 3),
        "father_of_rich_night",      Map.of("atk", 18, "maxHp", 5),
        "scribe_of_heaven",          Map.of("insight", 8, "atk", 5, "def", 5),
        "morning_star",              Map.of("atk", 5, "def", 5, "spd", 5, "maxHp", 5)
    );

    private static final Map<String, String> CONSTELLATION_NAMES = Map.of(
        "demon_judge_of_fire",       "惡魔般的火之審判者",
        "master_of_steel",           "鋼鐵之主",
        "prisoner_of_golden_headband", "金箍棒囚徒",
        "abyssal_black_flame_dragon", "深淵黑色焰龍",
        "queen_of_darkest_spring",   "最黑暗春天的女王",
        "father_of_rich_night",      "富裕夜晚之父",
        "scribe_of_heaven",          "天堂的抄寫員",
        "morning_star",              "晨星"
    );

    private Map<String, Object> getConstellationEffects(String constellationKey) {
        return CONSTELLATION_EFFECTS.get(constellationKey);
    }
}
