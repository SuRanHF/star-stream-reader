package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.vo.LogEntry;
import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
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
    private final ObjectMapper objectMapper;

    public PlayerServiceImpl(PlayerMapper playerMapper,
                             PlayerLogMapper playerLogMapper,
                             ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.objectMapper = objectMapper;
    }

    // ── 默认属性 JSON (与 Node.js defaultStats 保持一致) ──

    private static final String DEFAULT_STATS_JSON =
        "{" +
        "\"level\":1,\"exp\":0,\"hp\":100,\"maxHp\":100," +
        "\"attack\":10,\"defense\":5,\"speed\":10," +
        "\"critRate\":0.05,\"critDamage\":1.5," +
        "\"stamina\":50,\"maxStamina\":50," +
        "\"explorationPower\":1,\"luck\":1,\"dropRate\":0," +
        "\"rating\":1000,\"pkWins\":0,\"pkLosses\":0,\"pkStreak\":0," +
        "\"worldLineShift\":0,\"channelHeat\":0," +
        "\"freePoints\":40," +
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
                .currentLocation(player.getCurrentLocation())
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
}
