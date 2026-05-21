package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
public class QuestServiceImpl implements QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestServiceImpl.class);

    private final QuestMapper questMapper;
    private final PlayerQuestMapper playerQuestMapper;
    private final QuestProgressLogMapper progressLogMapper;
    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final InventoryService inventoryService;
    private final TitleService titleService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DAILY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;

    public QuestServiceImpl(QuestMapper questMapper,
                            PlayerQuestMapper playerQuestMapper,
                            QuestProgressLogMapper progressLogMapper,
                            PlayerMapper playerMapper,
                            PlayerLogMapper playerLogMapper,
                            InventoryService inventoryService,
                            TitleService titleService,
                            ObjectMapper objectMapper) {
        this.questMapper = questMapper;
        this.playerQuestMapper = playerQuestMapper;
        this.progressLogMapper = progressLogMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.inventoryService = inventoryService;
        this.titleService = titleService;
        this.objectMapper = objectMapper;
    }

    // ─── public API ───

    @Override
    public List<PlayerQuestVO> getAvailableQuests(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);
        ensurePlayerQuests(playerId);
        return getPlayerQuests(playerId);
    }

    @Override
    @Transactional
    public void ensurePlayerQuests(Long playerId) {
        LocalDate today = LocalDate.now();
        String dailyCycle = today.format(DAILY_FMT);
        String weeklyCycle = today.getYear() + "-W" + String.format("%02d", today.get(WEEK_FIELDS.weekOfWeekBasedYear()));

        // 获取所有启用任务定义
        QueryWrapper<Quest> dq = new QueryWrapper<>();
        dq.eq("enabled", 1);
        List<Quest> allQuests = questMapper.selectList(dq);

        for (Quest quest : allQuests) {
            String cycleKey;
            if ("daily".equals(quest.getQuestType())) {
                cycleKey = dailyCycle;
            } else if ("weekly".equals(quest.getQuestType())) {
                cycleKey = weeklyCycle;
            } else {
                cycleKey = "none";
            }

            // 检查是否已存在
            QueryWrapper<PlayerQuest> eq = new QueryWrapper<>();
            eq.eq("player_id", playerId)
              .eq("quest_key", quest.getQuestKey())
              .eq("cycle_key", cycleKey);
            if (playerQuestMapper.selectCount(eq) > 0) continue;

            PlayerQuest pq = new PlayerQuest();
            pq.setPlayerId(playerId);
            pq.setQuestKey(quest.getQuestKey());
            pq.setQuestType(quest.getQuestType());
            pq.setStatus("active");
            pq.setProgress(0);
            pq.setTargetValue(quest.getTargetValue());
            pq.setRewardClaimed(0);
            pq.setCycleKey(cycleKey);
            pq.setAcceptedAt(LocalDateTime.now());
            pq.setCreatedAt(LocalDateTime.now());
            pq.setUpdatedAt(LocalDateTime.now());
            playerQuestMapper.insert(pq);
        }
    }

    @Override
    @Transactional
    public List<PlayerQuestVO> addProgress(Long playerId, String targetType, int delta, String source, String relatedId) {
        if (delta <= 0) return Collections.emptyList();

        // 查询玩家所有 active 任务中 targetType 匹配的
        QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId)
          .eq("status", "active");
        List<PlayerQuest> activeQuests = playerQuestMapper.selectList(qw);

        List<PlayerQuestVO> changed = new ArrayList<>();
        for (PlayerQuest pq : activeQuests) {
            Quest quest = questMapper.selectOne(
                    new QueryWrapper<Quest>().eq("quest_key", pq.getQuestKey()));
            if (quest == null) continue;
            if (!targetType.equals(quest.getTargetType())) continue;

            int newProgress = Math.min(pq.getTargetValue(), pq.getProgress() + delta);
            int actualDelta = newProgress - pq.getProgress();
            if (actualDelta <= 0) continue;

            pq.setProgress(newProgress);
            pq.setUpdatedAt(LocalDateTime.now());

            if (newProgress >= pq.getTargetValue()) {
                pq.setStatus("completed");
                pq.setCompletedAt(LocalDateTime.now());
            }

            playerQuestMapper.updateById(pq);

            // 写进度日志
            QuestProgressLog log = new QuestProgressLog();
            log.setPlayerId(playerId);
            log.setQuestKey(pq.getQuestKey());
            log.setDelta(actualDelta);
            log.setProgressBefore(pq.getProgress() - actualDelta);
            log.setProgressAfter(newProgress);
            log.setSource(source);
            log.setRelatedId(relatedId);
            log.setCreatedAt(LocalDateTime.now());
            progressLogMapper.insert(log);

            changed.add(toPlayerQuestVO(pq, quest));
        }

        return changed;
    }

    @Override
    @Transactional
    public QuestRewardVO claimReward(Long playerId, String questKey, String cycleKey, Long userId) {
        validatePlayerOwnership(playerId, userId);

        // 兼容前端 questId (数字) → 解析为 questKey + cycleKey
        PlayerQuest pq = null;
        if (cycleKey != null && !cycleKey.isBlank()) {
            QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
            qw.eq("player_id", playerId)
              .eq("quest_key", questKey)
              .eq("cycle_key", cycleKey);
            pq = playerQuestMapper.selectOne(qw);
        }
        if (pq == null && questKey != null && questKey.matches("\\d+")) {
            // questKey 是纯数字 → 按 player_quest.id 查找
            pq = playerQuestMapper.selectById(Long.parseLong(questKey));
        }
        if (pq == null) {
            // 尝试只按 questKey 查找（cycleKey 可能不符）
            QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
            qw.eq("player_id", playerId).eq("quest_key", questKey);
            pq = playerQuestMapper.selectOne(qw);
        }
        if (pq == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");

        if (!"completed".equals(pq.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "任务未完成，不能领奖");

        if ("expired".equals(pq.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "任务已过期");

        if (pq.getRewardClaimed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "奖励已领取");

        String resolvedQuestKey = pq.getQuestKey();
        Quest quest = questMapper.selectOne(
                new QueryWrapper<Quest>().eq("quest_key", resolvedQuestKey));
        if (quest == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务定义不存在");

        Map<String, Object> rewardsObj = parseJsonMap(quest.getRewardsJson());
        Map<String, Object> earned = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();

        Player player = playerMapper.selectById(playerId);

        // coins
        int coins = toInt(rewardsObj.get("coins"), 0);
        if (coins > 0) {
            player.setCoins(player.getCoins() + coins);
            earned.put("coins", coins);
        }

        // storyFragments
        int fragments = toInt(rewardsObj.get("storyFragments"), 0);
        if (fragments > 0) {
            player.setStoryFragments(player.getStoryFragments() + fragments);
            earned.put("storyFragments", fragments);
        }

        // exp (存入 stats_json)
        int exp = toInt(rewardsObj.get("exp"), 0);
        if (exp > 0) {
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            int currentExp = toInt(stats.get("exp"), 0);
            stats.put("exp", currentExp + exp);
            try {
                player.setStatsJson(objectMapper.writeValueAsString(stats));
            } catch (Exception ignored) {}
            earned.put("exp", exp);
        }

        // channelHeat
        int channelHeat = toInt(rewardsObj.get("channelHeat"), 0);
        if (channelHeat > 0) {
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            int current = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", current + channelHeat);
            try {
                player.setStatsJson(objectMapper.writeValueAsString(stats));
            } catch (Exception ignored) {}
            earned.put("channelHeat", channelHeat);
        }

        // items
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rewardsObj.get("items");
        if (items != null) {
            List<Map<String, Object>> acquired = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String itemKey = (String) item.get("itemKey");
                int qty = toInt(item.get("quantity"), 1);
                if (itemKey != null && qty > 0) {
                    try {
                        inventoryService.addItem(playerId, itemKey, qty);
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("itemKey", itemKey);
                        a.put("quantity", qty);
                        acquired.add(a);
                    } catch (Exception e) {
                        messages.add("物品发放失败: " + itemKey + " - " + e.getMessage());
                    }
                }
            }
            if (!acquired.isEmpty()) earned.put("items", acquired);
        }

        // titleKey
        String titleKey = (String) rewardsObj.get("titleKey");
        if (titleKey != null && !titleKey.isBlank()) {
            try {
                titleService.grantTitle(playerId, titleKey);
                earned.put("titleKey", titleKey);
                messages.add("获得称号: " + titleKey);
            } catch (Exception e) {
                log.warn("Quest reward title grant failed: player={}, titleKey={}", playerId, titleKey, e);
                messages.add("称号发放失败: " + titleKey + " (已跳过)");
            }
        }

        playerMapper.updateById(player);

        // 标记已领取
        pq.setStatus("claimed");
        pq.setRewardClaimed(1);
        pq.setClaimedAt(LocalDateTime.now());
        pq.setUpdatedAt(LocalDateTime.now());
        playerQuestMapper.updateById(pq);

        writeLog(playerId, "quest", "领取了任务「" + quest.getTitle() + "」的奖励");

        QuestRewardVO vo = new QuestRewardVO();
        vo.setClaimed(true);
        vo.setQuestKey(resolvedQuestKey);
        vo.setQuestTitle(quest.getTitle());
        vo.setEarned(earned);
        vo.setMessages(messages);
        return vo;
    }

    @Override
    @Transactional
    public int refreshDailyQuests() {
        String todayCycle = LocalDate.now().format(DAILY_FMT);
        return expireQuestsByType("daily", todayCycle);
    }

    @Override
    @Transactional
    public int refreshWeeklyQuests() {
        LocalDate today = LocalDate.now();
        String thisWeekCycle = today.getYear() + "-W" + String.format("%02d", today.get(WEEK_FIELDS.weekOfWeekBasedYear()));
        return expireQuestsByType("weekly", thisWeekCycle);
    }

    @Override
    public QuestSummaryVO getQuestSummary(Long playerId) {
        ensurePlayerQuests(playerId);

        QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId);
        List<PlayerQuest> all = playerQuestMapper.selectList(qw);

        int dailyTotal = 0, dailyCompleted = 0, dailyClaimable = 0;
        int weeklyTotal = 0, weeklyCompleted = 0, weeklyClaimable = 0;
        int achievementClaimable = 0;

        for (PlayerQuest pq : all) {
            switch (pq.getQuestType()) {
                case "daily":
                    dailyTotal++;
                    if ("completed".equals(pq.getStatus())) { dailyCompleted++; dailyClaimable++; }
                    else if ("claimed".equals(pq.getStatus())) dailyCompleted++;
                    break;
                case "weekly":
                    weeklyTotal++;
                    if ("completed".equals(pq.getStatus())) { weeklyCompleted++; weeklyClaimable++; }
                    else if ("claimed".equals(pq.getStatus())) weeklyCompleted++;
                    break;
                case "achievement":
                    if ("completed".equals(pq.getStatus())) achievementClaimable++;
                    break;
            }
        }

        QuestSummaryVO vo = new QuestSummaryVO();
        vo.setDailyTotal(dailyTotal);
        vo.setDailyCompleted(dailyCompleted);
        vo.setDailyClaimable(dailyClaimable);
        vo.setWeeklyTotal(weeklyTotal);
        vo.setWeeklyCompleted(weeklyCompleted);
        vo.setWeeklyClaimable(weeklyClaimable);
        vo.setAchievementClaimable(achievementClaimable);
        return vo;
    }

    @Override
    @Transactional
    public void evaluateGrowthQuests(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return;
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());

        int level = toInt(stats.get("level"), 1);
        String avatarRank = (String) stats.getOrDefault("avatarRank", "F");

        // 推进 level_reach 任务
        addProgress(playerId, "level_reach", level, "growth", "level_" + level);

        // 推进 avatar_rank_reach 任务 (特殊处理: 需要 conditions.avatarRank 匹配)
        checkAvatarRankQuests(playerId, avatarRank);
    }

    @Override
    public List<QuestVO> getQuestDefinitions() {
        QueryWrapper<Quest> qw = new QueryWrapper<>();
        qw.eq("enabled", 1).orderByAsc("quest_type", "sort_order");
        return questMapper.selectList(qw).stream()
                .map(this::toQuestVO)
                .collect(java.util.stream.Collectors.toList());
    }

    // ─── internal ───

    private List<PlayerQuestVO> getPlayerQuests(Long playerId) {
        QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId)
          .orderByAsc("quest_type", "status", "id");
        List<PlayerQuest> list = playerQuestMapper.selectList(qw);

        List<PlayerQuestVO> result = new ArrayList<>();
        for (PlayerQuest pq : list) {
            Quest quest = questMapper.selectOne(
                    new QueryWrapper<Quest>().eq("quest_key", pq.getQuestKey()));
            result.add(toPlayerQuestVO(pq, quest));
        }
        return result;
    }

    private PlayerQuestVO toPlayerQuestVO(PlayerQuest pq, Quest quest) {
        PlayerQuestVO vo = new PlayerQuestVO();
        vo.setId(pq.getId());
        vo.setPlayerId(pq.getPlayerId());
        vo.setQuestKey(pq.getQuestKey());
        vo.setQuestType(pq.getQuestType());
        vo.setTitle(quest != null ? quest.getTitle() : pq.getQuestKey());
        vo.setDescription(quest != null ? quest.getDescription() : "");
        vo.setCategory(quest != null ? quest.getCategory() : "");
        vo.setTargetType(quest != null ? quest.getTargetType() : "");
        vo.setTargetValue(pq.getTargetValue());
        vo.setStatus(pq.getStatus());
        vo.setProgress(pq.getProgress());
        vo.setRewardClaimed(pq.getRewardClaimed());
        vo.setCycleKey(pq.getCycleKey());
        vo.setRewards(quest != null ? parseJsonMap(quest.getRewardsJson()) : new LinkedHashMap<>());
        vo.setAcceptedAt(pq.getAcceptedAt() != null ? pq.getAcceptedAt().toString() : null);
        vo.setCompletedAt(pq.getCompletedAt() != null ? pq.getCompletedAt().toString() : null);
        vo.setClaimedAt(pq.getClaimedAt() != null ? pq.getClaimedAt().toString() : null);
        return vo;
    }

    private QuestVO toQuestVO(Quest q) {
        QuestVO vo = new QuestVO();
        vo.setId(q.getId());
        vo.setQuestKey(q.getQuestKey());
        vo.setTitle(q.getTitle());
        vo.setDescription(q.getDescription());
        vo.setQuestType(q.getQuestType());
        vo.setCategory(q.getCategory());
        vo.setTargetType(q.getTargetType());
        vo.setTargetValue(q.getTargetValue());
        vo.setConditions(parseJsonMap(q.getConditionsJson()));
        vo.setRewards(parseJsonMap(q.getRewardsJson()));
        vo.setResetCycle(q.getResetCycle());
        vo.setSortOrder(q.getSortOrder());
        vo.setEnabled(q.getEnabled());
        return vo;
    }

    private int expireQuestsByType(String questType, String currentCycle) {
        UpdateWrapper<PlayerQuest> uw = new UpdateWrapper<>();
        uw.eq("quest_type", questType)
          .ne("cycle_key", currentCycle)
          .in("status", Arrays.asList("active", "completed"))
          .eq("reward_claimed", 0)
          .set("status", "expired")
          .set("updated_at", LocalDateTime.now());
        return playerQuestMapper.update(null, uw);
    }

    @SuppressWarnings("unchecked")
    private void checkAvatarRankQuests(Long playerId, String avatarRank) {
        QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId)
          .eq("status", "active");
        List<PlayerQuest> activeQuests = playerQuestMapper.selectList(qw);

        for (PlayerQuest pq : activeQuests) {
            Quest quest = questMapper.selectOne(
                    new QueryWrapper<Quest>().eq("quest_key", pq.getQuestKey()));
            if (quest == null || !"avatar_rank_reach".equals(quest.getTargetType())) continue;

            Map<String, Object> conditions = parseJsonMap(quest.getConditionsJson());
            String requiredRank = (String) conditions.get("avatarRank");
            if (requiredRank == null) continue;

            // 排名系统：F < E < D < C < B < A < S < SS < SSS
            if (rankValue(avatarRank) >= rankValue(requiredRank)) {
                if (pq.getProgress() < pq.getTargetValue()) {
                    pq.setProgress(pq.getTargetValue());
                    pq.setStatus("completed");
                    pq.setCompletedAt(LocalDateTime.now());
                    pq.setUpdatedAt(LocalDateTime.now());
                    playerQuestMapper.updateById(pq);
                }
            }
        }
    }

    private int rankValue(String rank) {
        return switch (rank.toUpperCase()) {
            case "F" -> 0;
            case "E" -> 1;
            case "D" -> 2;
            case "C" -> 3;
            case "B" -> 4;
            case "A" -> 5;
            case "S" -> 6;
            case "SS" -> 7;
            case "SSS" -> 8;
            default -> -1;
        };
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    private void writeLog(Long playerId, String type, String message) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        playerLogMapper.insert(log);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
