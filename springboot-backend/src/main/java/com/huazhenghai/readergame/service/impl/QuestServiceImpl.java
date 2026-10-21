package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final AvatarRankService avatarRankService;
    private final PlayerEffectService playerEffectService;
    private final PlayerActionReceiptService actionReceiptService;
    private final PlayerResourceTransactionService resourceTransactionService;
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
                            AvatarRankService avatarRankService,
                            ObjectMapper objectMapper,
                            PlayerEffectService playerEffectService,
                            PlayerActionReceiptService actionReceiptService,
                            PlayerResourceTransactionService resourceTransactionService) {
        this.questMapper = questMapper;
        this.playerQuestMapper = playerQuestMapper;
        this.progressLogMapper = progressLogMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.inventoryService = inventoryService;
        this.titleService = titleService;
        this.avatarRankService = avatarRankService;
        this.objectMapper = objectMapper;
        this.playerEffectService = playerEffectService;
        this.actionReceiptService = actionReceiptService;
        this.resourceTransactionService = resourceTransactionService;
    }

    // ─── public API ───

    @Override
    @Transactional
    public List<PlayerQuestVO> getAvailableQuests(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);
        ensurePlayerQuests(playerId);
        evaluateGrowthQuests(playerId);
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
    // Optional progress must roll back to a savepoint when callers intentionally keep the main action.
    @Transactional(propagation = Propagation.NESTED)
    public List<PlayerQuestVO> addProgress(Long playerId, String targetType, int delta, String source, String relatedId) {
        if (delta <= 0) return Collections.emptyList();

        // 锁定 active 任务，避免并发的探索/战斗进度更新互相覆盖。
        List<PlayerQuest> activeQuests = playerQuestMapper.selectActiveByPlayerForUpdate(playerId);

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

            if (playerQuestMapper.updateById(pq) <= 0) {
                throw new IllegalStateException("任务进度写入失败: " + pq.getQuestKey());
            }
            if ("completed".equals(pq.getStatus())) {
                if (writeQuestCompletionLog(playerId, quest, pq) <= 0) {
                    throw new IllegalStateException("任务完成日志写入失败: " + pq.getQuestKey());
                }
            }

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
            if (progressLogMapper.insert(log) <= 0) {
                throw new IllegalStateException("任务进度日志写入失败: " + pq.getQuestKey());
            }

            changed.add(toPlayerQuestVO(pq, quest));
        }

        return changed;
    }

    @Override
    @Transactional
    public QuestRewardVO claimReward(Long playerId, String questKey, String cycleKey, Long userId) {
        return claimReward(playerId, questKey, cycleKey, userId, null);
    }

    @Override
    @Transactional
    public QuestRewardVO claimReward(Long playerId, String questKey, String cycleKey,
                                     Long userId, String requestKey) {
        // 先锁玩家行，使同一玩家的并发领奖串行，避免不同任务的资源更新互相覆盖。
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        if (questKey == null || questKey.isBlank())
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "任务标识不能为空");

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("playerId", playerId);
        requestPayload.put("questKey", questKey);
        requestPayload.put("cycleKey", cycleKey);
        String effectiveRequestKey = requestKey == null || requestKey.isBlank()
                ? createLegacyClaimRequestKey(playerId, questKey, cycleKey)
                : requestKey.trim();
        QuestRewardVO replay = actionReceiptService.findReplay(
                playerId, "quest.claim", effectiveRequestKey, requestPayload, QuestRewardVO.class);
        if (replay != null) return replay;

        // 兼容前端 questId (数字) → 解析为 questKey + cycleKey
        PlayerQuest pq = null;
        if (cycleKey != null && !cycleKey.isBlank()) {
            QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
            qw.eq("player_id", playerId)
              .eq("quest_key", questKey)
              .eq("cycle_key", cycleKey)
              .last("FOR UPDATE");
            pq = playerQuestMapper.selectOne(qw);
        }
        if (pq == null && questKey != null && questKey.matches("\\d+")) {
            // questKey 是纯数字 → 按 player_quest.id 查找
            pq = playerQuestMapper.selectByIdForUpdate(Long.parseLong(questKey));
            // 数字 ID 兼容分支也必须校验归属，避免把其他玩家的任务标记为已领取。
            if (pq != null && !Objects.equals(pq.getPlayerId(), playerId)) pq = null;
        }
        if (pq == null) {
            // 尝试只按 questKey 查找（cycleKey 可能不符）
            QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
            qw.eq("player_id", playerId).eq("quest_key", questKey).last("FOR UPDATE");
            pq = playerQuestMapper.selectOne(qw);
        }
        if (pq == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");

        if ("claimed".equals(pq.getStatus()) || Objects.equals(pq.getRewardClaimed(), 1))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "奖励已领取");

        if ("expired".equals(pq.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "任务已过期");

        if (!"completed".equals(pq.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "任务未完成，不能领奖");

        String resolvedQuestKey = pq.getQuestKey();
        Quest quest = questMapper.selectOne(
                new QueryWrapper<Quest>().eq("quest_key", resolvedQuestKey));
        if (quest == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务定义不存在");

        Map<String, Object> rewardsObj = parseJsonMap(quest.getRewardsJson());
        Map<String, Object> earned = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();
        boolean playerChanged = false;
        String cycleKeyValue = Objects.toString(pq.getCycleKey(), "none");
        String transactionGroupId = UUID.nameUUIDFromBytes(
                (playerId + ":" + resolvedQuestKey + ":" + cycleKeyValue)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String sourceId = transactionGroupId;
        int coinsBefore = player.getCoins() != null ? player.getCoins() : 0;
        int fragmentsBefore = player.getStoryFragments() != null ? player.getStoryFragments() : 0;
        int coinsAfter = coinsBefore;
        int fragmentsAfter = fragmentsBefore;
        int experienceBefore = 0;
        int experienceAfter = 0;
        int channelHeatBefore = 0;
        int channelHeatAfter = 0;

        // coins
        int coins = toInt(rewardsObj.get("coins"), 0);
        if (coins > 0) {
            coinsAfter = ResourceBalance.applyDelta("星币", coinsBefore, coins);
            player.setCoins(coinsAfter);
            earned.put("coins", coins);
            playerChanged = true;
        }

        // storyFragments
        int fragments = toInt(rewardsObj.get("storyFragments"), 0);
        if (fragments > 0) {
            fragmentsAfter = ResourceBalance.applyDelta("故事碎片", fragmentsBefore, fragments);
            player.setStoryFragments(fragmentsAfter);
            earned.put("storyFragments", fragments);
            playerChanged = true;
        }

        // exp (位阶倍率加成，存入 stats_json)
        int exp = toInt(rewardsObj.get("exp"), 0);
        if (exp > 0) {
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            experienceBefore = toInt(stats.get("exp"), 0);
            double multiplier = avatarRankService.getExpMultiplier(
                    (String) stats.getOrDefault("avatarRank", "F"));
            ExperienceProgression.Result progression = ExperienceProgression.addExperience(stats, exp, multiplier);
            experienceAfter = toInt(stats.get("exp"), experienceBefore);
            serializeRewardStats(player, stats);
            earned.put("exp", progression.experienceAwarded());
            playerChanged = true;
            String levelUpMessage = ExperienceProgression.levelUpMessage(progression);
            if (levelUpMessage != null) writeLog(playerId, "milestone", levelUpMessage);
        }

        // channelHeat
        int channelHeat = playerEffectService.applyChannelHeatGainRate(
                playerId, toInt(rewardsObj.get("channelHeat"), 0));
        if (channelHeat > 0) {
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            int current = toInt(stats.get("channelHeat"), 0);
            channelHeatBefore = current;
            channelHeatAfter = Math.addExact(current, channelHeat);
            stats.put("channelHeat", channelHeatAfter);
            serializeRewardStats(player, stats);
            earned.put("channelHeat", channelHeat);
            playerChanged = true;
        }

        // items
        int itemRewardIndex = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rewardsObj.get("items");
        if (items != null) {
            List<Map<String, Object>> acquired = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String itemKey = (String) item.get("itemKey");
                int qty = toInt(item.get("quantity"), 1);
                if (itemKey != null && qty > 0) {
                    var grant = inventoryService.addItemWithResult(playerId, itemKey, qty);
                    if (grant.quantityGranted() != qty) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                                "背包堆叠空间不足或奖励物品不可用，处理后再领取任务奖励");
                    }
                    int itemBalanceAfter = grant.totalQuantity();
                    recordQuestResourceChange(playerId, "item", itemKey,
                            itemBalanceAfter - grant.quantityGranted(), itemBalanceAfter,
                            sourceId, transactionGroupId,
                            transactionGroupId + ":item:" + itemKey + ":" + itemRewardIndex,
                            resolvedQuestKey, cycleKeyValue, quest.getTitle());
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("itemKey", itemKey);
                    a.put("quantity", grant.quantityGranted());
                    acquired.add(a);
                }
                itemRewardIndex++;
            }
            if (!acquired.isEmpty()) earned.put("items", acquired);
        }

        // titleKey
        String titleKey = (String) rewardsObj.get("titleKey");
        if (titleKey != null && !titleKey.isBlank()) {
            titleService.grantTitle(playerId, titleKey);
            earned.put("titleKey", titleKey);
            messages.add("获得称号: " + titleKey);
        }

        if (playerChanged && playerMapper.updateById(player) <= 0) {
            throw new IllegalStateException("任务奖励写入失败，玩家资源未保存");
        }

        recordQuestResourceChange(playerId, "currency", "coins", coinsBefore, coinsAfter,
                sourceId, transactionGroupId, transactionGroupId + ":currency:coins",
                resolvedQuestKey, cycleKeyValue, quest.getTitle());
        recordQuestResourceChange(playerId, "currency", "story_fragments",
                fragmentsBefore, fragmentsAfter, sourceId, transactionGroupId,
                transactionGroupId + ":currency:story_fragments",
                resolvedQuestKey, cycleKeyValue, quest.getTitle());
        recordQuestResourceChange(playerId, "experience", "exp",
                experienceBefore, experienceAfter, sourceId, transactionGroupId,
                transactionGroupId + ":experience:exp",
                resolvedQuestKey, cycleKeyValue, quest.getTitle());
        recordQuestResourceChange(playerId, "progress", "channel_heat",
                channelHeatBefore, channelHeatAfter, sourceId, transactionGroupId,
                transactionGroupId + ":progress:channel_heat",
                resolvedQuestKey, cycleKeyValue, quest.getTitle());

        // 标记已领取
        pq.setStatus("claimed");
        pq.setRewardClaimed(1);
        pq.setClaimedAt(LocalDateTime.now());
        pq.setUpdatedAt(LocalDateTime.now());
        if (playerQuestMapper.updateById(pq) <= 0) {
            throw new IllegalStateException("任务领取状态写入失败");
        }

        Map<String, Object> rewardLogMetadata = new LinkedHashMap<>();
        rewardLogMetadata.put("kind", "quest_reward");
        rewardLogMetadata.put("questKey", resolvedQuestKey);
        rewardLogMetadata.put("questTitle", quest.getTitle());
        rewardLogMetadata.put("cycleKey", pq.getCycleKey());
        rewardLogMetadata.put("rewards", new LinkedHashMap<>(earned));
        rewardLogMetadata.put("messages", new ArrayList<>(messages));
        writeRequiredLog(playerId, "quest", "领取了任务「" + quest.getTitle() + "」的奖励", rewardLogMetadata);

        QuestRewardVO vo = new QuestRewardVO();
        vo.setClaimed(true);
        vo.setQuestKey(resolvedQuestKey);
        vo.setQuestTitle(quest.getTitle());
        vo.setEarned(earned);
        vo.setMessages(messages);
        actionReceiptService.record(playerId, "quest.claim", effectiveRequestKey, requestPayload, vo);
        return vo;
    }

    private String createLegacyClaimRequestKey(Long playerId, String questKey, String cycleKey) {
        String identity = "quest-claim:" + playerId + ":" + questKey + ":" + Objects.toString(cycleKey, "");
        return UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private void recordQuestResourceChange(Long playerId, String resourceType, String resourceKey,
                                           long balanceBefore, long balanceAfter, String sourceId,
                                           String transactionGroupId, String idempotencyKey,
                                           String questKey, String cycleKey, String questTitle) {
        long amount = balanceAfter - balanceBefore;
        if (amount == 0) return;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("questKey", questKey);
        metadata.put("cycleKey", cycleKey);
        metadata.put("questTitle", Objects.toString(questTitle, ""));
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, balanceBefore, balanceAfter,
                "quest_reward", sourceId, transactionGroupId, idempotencyKey, metadata));
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
    @Transactional
    public QuestSummaryVO getQuestSummary(Long playerId) {
        ensurePlayerQuests(playerId);
        // Bootstrap 和任务面板都会读摘要；在返回前把等级/位阶成就与玩家当前成长状态对齐。
        evaluateGrowthQuests(playerId);

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

        // 等级是“当前值”而非本次增量；只同步到该等级，避免每次读取任务摘要都重复累计。
        syncLevelReachQuests(playerId, level);

        // 推进 avatar_rank_reach 任务 (特殊处理: 需要 conditions.avatarRank 匹配)
        checkAvatarRankQuests(playerId, avatarRank);
    }

    private void syncLevelReachQuests(Long playerId, int level) {
        QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        List<PlayerQuest> activeQuests = playerQuestMapper.selectList(qw);

        for (PlayerQuest playerQuest : activeQuests) {
            Quest quest = questMapper.selectOne(
                    new QueryWrapper<Quest>().eq("quest_key", playerQuest.getQuestKey()));
            if (quest == null || !"level_reach".equals(quest.getTargetType())) continue;

            int target = toInt(playerQuest.getTargetValue(), toInt(quest.getTargetValue(), 0));
            if (target <= 0) continue;
            int before = toInt(playerQuest.getProgress(), 0);
            int after = Math.min(target, Math.max(0, level));
            int delta = after - before;
            if (delta <= 0) continue;

            playerQuest.setProgress(after);
            playerQuest.setUpdatedAt(LocalDateTime.now());
            if (after >= target) {
                playerQuest.setStatus("completed");
                playerQuest.setCompletedAt(LocalDateTime.now());
            }
            playerQuestMapper.updateById(playerQuest);
            if ("completed".equals(playerQuest.getStatus())) {
                writeQuestCompletionLog(playerId, quest, playerQuest);
            }
            recordProgressLog(playerId, playerQuest.getQuestKey(), delta, before, after,
                    "growth", "level_" + level);
        }
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
        QueryWrapper<PlayerQuest> qw = new QueryWrapper<>();
        qw.eq("quest_type", questType)
          .ne("cycle_key", currentCycle)
          .in("status", Arrays.asList("active", "completed"))
          .eq("reward_claimed", 0)
          .last("FOR UPDATE");
        List<PlayerQuest> expiring = playerQuestMapper.selectList(qw);
        if (expiring.isEmpty()) return 0;

        List<String> completedKeys = expiring.stream()
                .filter(pq -> "completed".equals(pq.getStatus()))
                .map(PlayerQuest::getQuestKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Quest> definitions = completedKeys.isEmpty()
                ? Collections.emptyMap()
                : questMapper.selectList(new QueryWrapper<Quest>().in("quest_key", completedKeys)).stream()
                        .collect(Collectors.toMap(Quest::getQuestKey, Function.identity(), (first, ignored) -> first));

        LocalDateTime now = LocalDateTime.now();
        int expiredCount = 0;
        for (PlayerQuest playerQuest : expiring) {
            boolean completed = "completed".equals(playerQuest.getStatus());
            playerQuest.setStatus("expired");
            playerQuest.setUpdatedAt(now);
            if (playerQuestMapper.updateById(playerQuest) <= 0) continue;
            expiredCount++;

            // Make the loss of an unclaimed completed reward visible in the same durable log as other outcomes.
            if (completed) {
                Quest quest = definitions.get(playerQuest.getQuestKey());
                String title = quest != null && quest.getTitle() != null && !quest.getTitle().isBlank()
                        ? quest.getTitle() : playerQuest.getQuestKey();
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("kind", "quest_expired");
                metadata.put("questKey", playerQuest.getQuestKey());
                metadata.put("questTitle", title);
                metadata.put("cycleKey", playerQuest.getCycleKey());
                metadata.put("progress", playerQuest.getProgress());
                metadata.put("targetValue", playerQuest.getTargetValue());
                writeLog(playerQuest.getPlayerId(), "quest",
                        "任务「" + title + "」已过期，未领取奖励已失效", metadata);
            }
        }
        return expiredCount;
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
            // 兼容初始 achv_rank_C 定义未写 conditions_json 的旧数据。
            if ((requiredRank == null || requiredRank.isBlank())
                    && quest.getQuestKey() != null
                    && quest.getQuestKey().startsWith("achv_rank_")) {
                requiredRank = quest.getQuestKey().substring("achv_rank_".length());
            }
            if (requiredRank == null) continue;

            // 排名系统：F < E < D < C < B < A < S < SS < SSS
            if (rankValue(avatarRank) >= rankValue(requiredRank)) {
                if (pq.getProgress() < pq.getTargetValue()) {
                    int before = toInt(pq.getProgress(), 0);
                    pq.setProgress(pq.getTargetValue());
                    pq.setStatus("completed");
                    pq.setCompletedAt(LocalDateTime.now());
                    pq.setUpdatedAt(LocalDateTime.now());
                    playerQuestMapper.updateById(pq);
                    writeQuestCompletionLog(playerId, quest, pq);
                    recordProgressLog(playerId, pq.getQuestKey(), pq.getProgress() - before,
                            before, pq.getProgress(), "growth", "rank_" + avatarRank);
                }
            }
        }
    }

    private void recordProgressLog(Long playerId, String questKey, int delta, int before, int after,
                                   String source, String relatedId) {
        QuestProgressLog progressLog = new QuestProgressLog();
        progressLog.setPlayerId(playerId);
        progressLog.setQuestKey(questKey);
        progressLog.setDelta(delta);
        progressLog.setProgressBefore(before);
        progressLog.setProgressAfter(after);
        progressLog.setSource(source);
        progressLog.setRelatedId(relatedId);
        progressLog.setCreatedAt(LocalDateTime.now());
        progressLogMapper.insert(progressLog);
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

    private int writeLog(Long playerId, String type, String message) {
        return writeLog(playerId, type, message, null);
    }

    private int writeLog(Long playerId, String type, String message, Map<String, Object> metadata) {
        return writeLog(playerId, type, message, metadata, false);
    }

    private int writeRequiredLog(Long playerId, String type, String message, Map<String, Object> metadata) {
        return writeLog(playerId, type, message, metadata, true);
    }

    private int writeLog(Long playerId, String type, String message,
                         Map<String, Object> metadata, boolean required) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        if (metadata != null) {
            try {
                log.setMetadataJson(objectMapper.writeValueAsString(metadata));
            } catch (Exception exception) {
                if (required) {
                    throw new IllegalStateException("任务奖励日志元数据序列化失败", exception);
                }
                QuestServiceImpl.log.warn("Failed to serialize quest log metadata for player {}", playerId, exception);
            }
        }
        int inserted = playerLogMapper.insert(log);
        if (required && inserted <= 0) {
            throw new IllegalStateException("任务奖励日志写入失败");
        }
        return inserted;
    }

    private int writeQuestCompletionLog(Long playerId, Quest quest, PlayerQuest playerQuest) {
        String title = quest.getTitle() != null && !quest.getTitle().isBlank()
                ? quest.getTitle() : playerQuest.getQuestKey();
        return writeLog(playerId, "quest", "完成任务「" + title + "」，奖励可领取");
    }

    private void serializeRewardStats(Player player, Map<String, Object> stats) {
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception exception) {
            throw new IllegalStateException("任务奖励状态序列化失败", exception);
        }
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
