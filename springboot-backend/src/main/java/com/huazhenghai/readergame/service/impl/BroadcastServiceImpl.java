package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.BroadcastContribution;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.BroadcastContributionMapper;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.BroadcastEventVO;
import com.huazhenghai.readergame.vo.BroadcastHistoryVO;
import com.huazhenghai.readergame.vo.BroadcastSummaryVO;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BroadcastServiceImpl implements BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastServiceImpl.class);
    private static final Set<String> AI_OBJECTIVE_TYPES = Set.of(
            "explore_location", "trigger_story", "defeat_boss", "kill_monster", "win_pk");
    private static final Map<String, String> AI_OBJECTIVE_LABELS = Map.of(
            "explore_location", "完成探索",
            "trigger_story", "触发故事遭遇",
            "defeat_boss", "击破世界首领",
            "kill_monster", "击败怪物",
            "win_pk", "完成玩家对决");
    private static final List<Map.Entry<String, String>> WORLDLINE_EFFECT_LABELS = List.of(
            Map.entry("worldline_shift", "世界线偏移"),
            Map.entry("starstream_attention", "星流关注"),
            Map.entry("scenario_pressure", "剧本压力"));

    private final BroadcastEventMapper eventMapper;
    private final BroadcastContributionMapper contributionMapper;
    private final WorldlineService worldlineService;
    private final ChatService chatService;
    private final PlayerMapper playerMapper;
    private final InventoryService inventoryService;
    private final PlayerEffectService playerEffectService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;

    public BroadcastServiceImpl(BroadcastEventMapper eventMapper,
                                BroadcastContributionMapper contributionMapper,
                                WorldlineService worldlineService,
                                ChatService chatService,
                                PlayerMapper playerMapper,
                                InventoryService inventoryService,
                                ObjectMapper objectMapper,
                                WebSocketSessionManager sessionManager,
                                PlayerEffectService playerEffectService,
                                PlayerResourceTransactionService resourceTransactionService) {
        this.eventMapper = eventMapper;
        this.contributionMapper = contributionMapper;
        this.worldlineService = worldlineService;
        this.chatService = chatService;
        this.playerMapper = playerMapper;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.playerEffectService = playerEffectService;
        this.resourceTransactionService = resourceTransactionService;
    }

    @Override
    public List<BroadcastEventVO> getActiveBroadcasts() {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<BroadcastEvent> qw = new QueryWrapper<>();
        qw.eq("status", "active")
          .and(q -> q.isNull("start_at").or().le("start_at", now))
          .and(q -> q.isNull("end_at").or().gt("end_at", now))
          .orderByDesc("created_at");
        List<BroadcastEvent> events = eventMapper.selectList(qw);

        List<BroadcastEventVO> result = new ArrayList<>();
        for (BroadcastEvent e : events) {
            if (isWithinActiveWindow(e, now)) result.add(toVO(e));
        }
        return result;
    }

    @Override
    public List<BroadcastHistoryVO> getHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<BroadcastEvent> qw = new QueryWrapper<>();
        qw.and(q -> q.eq("status", "completed")
                .or(expired -> expired.eq("status", "active")
                        .and(started -> started.isNull("start_at").or().le("start_at", now))
                        .isNotNull("end_at")
                        .le("end_at", now)))
          .orderByDesc("updated_at")
          .orderByDesc("id")
          .last("LIMIT " + safeLimit);

        List<BroadcastHistoryVO> history = new ArrayList<>();
        for (BroadcastEvent event : eventMapper.selectList(qw)) {
            boolean completed = "completed".equals(event.getStatus());
            int target = event.getTargetValue() != null ? Math.max(0, event.getTargetValue()) : 0;
            int current = event.getCurrentValue() != null ? Math.max(0, event.getCurrentValue()) : 0;

            BroadcastHistoryVO vo = new BroadcastHistoryVO();
            vo.setEventKey(event.getEventKey());
            vo.setTitle(event.getTitle());
            vo.setDescription(event.getDescription());
            vo.setType(event.getType());
            vo.setStatus(completed ? "completed" : "expired");
            vo.setTargetValue(target);
            vo.setCurrentValue(current);
            vo.setProgressPercent(target == 0 ? 0 : Math.min(100, (int) Math.round(current * 100.0 / target)));
            vo.setStartAt(event.getStartAt() != null ? event.getStartAt().toString() : null);
            vo.setEndAt(event.getEndAt() != null ? event.getEndAt().toString() : null);
            LocalDateTime endedAt = completed ? event.getUpdatedAt() : event.getEndAt();
            vo.setEndedAt(endedAt != null ? endedAt.toString() : null);
            // The configured completion effect is an actual outcome only after target completion.
            vo.setWorldlineEffectSummaries(completed
                    ? buildWorldlineEffectSummary(event.getWorldlineEffectsJson())
                    : List.of());
            history.add(vo);
        }
        return history;
    }

    @Override
    public BroadcastEventVO getBroadcastDetail(String eventKey) {
        BroadcastEvent event = getEventByKey(eventKey);
        return toVO(event);
    }

    @Override
    // Contribution must commit or roll back with the exploration/combat action that produced it.
    @Transactional(propagation = Propagation.NESTED)
    public Map<String, Object> contribute(String eventKey, Long playerId, int value, String contributionType) {
        BroadcastEvent event = getEventByKey(eventKey);

        LocalDateTime now = LocalDateTime.now();
        if (!"active".equals(event.getStatus()) || !isWithinActiveWindow(event, now)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该广播事件不在进行中");
        }

        if (contributionType == null || contributionType.isBlank()) {
            contributionType = "exploration";
        }

        // Upsert contribution record
        QueryWrapper<BroadcastContribution> cq = new QueryWrapper<>();
        cq.eq("event_key", eventKey)
          .eq("player_id", playerId);
        BroadcastContribution existing = contributionMapper.selectOne(cq);

        if (existing != null) {
            existing.setContributionValue(existing.getContributionValue() + value);
            existing.setLastContributedAt(LocalDateTime.now());
            if (contributionMapper.updateById(existing) <= 0) {
                throw new IllegalStateException("广播个人贡献写入失败: " + eventKey);
            }
        } else {
            BroadcastContribution bc = new BroadcastContribution();
            bc.setEventKey(eventKey);
            bc.setPlayerId(playerId);
            bc.setContributionValue(value);
            bc.setContributionType(contributionType);
            bc.setRewardClaimed(0);
            bc.setLastContributedAt(LocalDateTime.now());
            bc.setCreatedAt(LocalDateTime.now());
            bc.setUpdatedAt(LocalDateTime.now());
            if (contributionMapper.insert(bc) <= 0) {
                throw new IllegalStateException("广播个人贡献创建失败: " + eventKey);
            }
        }

        // Update event current_value
        UpdateWrapper<BroadcastEvent> uw = new UpdateWrapper<>();
        uw.eq("event_key", eventKey)
          .setSql("current_value = current_value + " + value);
        if (eventMapper.update(null, uw) <= 0) {
            throw new IllegalStateException("广播事件进度写入失败: " + eventKey);
        }

        // Refresh event to check completion
        BroadcastEvent refreshed = getEventByKey(eventKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventKey", eventKey);
        result.put("contributed", value);
        result.put("currentValue", refreshed.getCurrentValue());
        result.put("targetValue", refreshed.getTargetValue());

        // Check if target reached
        if (refreshed.getCurrentValue() >= refreshed.getTargetValue()
                && "active".equals(refreshed.getStatus())) {
            completeEvent(refreshed);
            result.put("completed", true);
            result.put("message", "事件目标达成！");
        } else {
            result.put("completed", false);
        }

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> claimReward(String eventKey, Long playerId) {
        // Keep the global lock order consistent with combat: player first, then reward record.
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }

        BroadcastEvent event = getEventByKey(eventKey);

        // Find player's contribution
        QueryWrapper<BroadcastContribution> cq = new QueryWrapper<>();
        cq.eq("event_key", eventKey)
          .eq("player_id", playerId)
          .last("FOR UPDATE");
        BroadcastContribution contribution = contributionMapper.selectOne(cq);

        if (contribution == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "你未参与此广播事件");
        }

        if (contribution.getRewardClaimed() == 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你已经领取过此奖励");
        }

        // Parse personal rewards to check min contribution
        Map<String, Object> personalRewards = parseJsonMap(event.getPersonalRewardsJson());
        if (!hasClaimableReward(personalRewards)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该广播暂未配置可领取的个人奖励");
        }
        int minContribution = toInt(personalRewards.get("minContribution"), 0);

        if (contribution.getContributionValue() < minContribution) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    "贡献值不足，需要至少" + minContribution + "点贡献");
        }

        // Grant actual rewards to player
        boolean playerStateChanged = false;
        int coinsBefore = player.getCoins() == null ? 0 : player.getCoins();
        int coinsAfter = coinsBefore;
        int coins = toInt(personalRewards.get("coins"), 0);
        if (coins > 0) {
            coinsAfter = ResourceBalance.applyDelta("星币", coinsBefore, coins);
            player.setCoins(coinsAfter);
            playerStateChanged = true;
        }
        int fragmentsBefore = player.getStoryFragments() == null ? 0 : player.getStoryFragments();
        int fragmentsAfter = fragmentsBefore;
        int fragments = toInt(personalRewards.get("storyFragments"), 0);
        if (fragments > 0) {
            fragmentsAfter = ResourceBalance.applyDelta("故事碎片", fragmentsBefore, fragments);
            player.setStoryFragments(fragmentsAfter);
            playerStateChanged = true;
        }
        int channelHeatBefore = 0;
        int channelHeatAfter = 0;
        int heat = playerEffectService.applyChannelHeatGainRate(
                playerId, toInt(personalRewards.get("channelHeat"), 0));
        if (heat > 0) {
            personalRewards.put("channelHeat", heat);
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            channelHeatBefore = toInt(stats.get("channelHeat"), 0);
            channelHeatAfter = Math.addExact(channelHeatBefore, heat);
            stats.put("channelHeat", channelHeatAfter);
            try {
                player.setStatsJson(objectMapper.writeValueAsString(stats));
            } catch (Exception exception) {
                throw new IllegalStateException("广播奖励热度保存失败", exception);
            }
            playerStateChanged = true;
        }
        if (playerStateChanged && playerMapper.updateById(player) <= 0) {
            throw new IllegalStateException("广播奖励资源保存失败");
        }

        String transactionGroupId = UUID.nameUUIDFromBytes(
                ("broadcast-reward:" + eventKey + ":" + playerId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Map<String, Object> resourceMetadata = Map.of(
                "eventKey", eventKey,
                "eventTitle", Objects.toString(event.getTitle(), ""),
                "contribution", contribution.getContributionValue());
        recordBroadcastResourceChange(playerId, "currency", "coins", coinsBefore, coinsAfter,
                eventKey, transactionGroupId, resourceMetadata);
        recordBroadcastResourceChange(playerId, "currency", "story_fragments", fragmentsBefore, fragmentsAfter,
                eventKey, transactionGroupId, resourceMetadata);
        recordBroadcastResourceChange(playerId, "progress", "channel_heat", channelHeatBefore, channelHeatAfter,
                eventKey, transactionGroupId, resourceMetadata);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) personalRewards.get("items");
        if (items != null) {
            List<Map<String, Object>> grantedItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String itemKey = (String) item.get("itemKey");
                int qty = toInt(item.get("quantity"), 1);
                if (itemKey != null && qty > 0) {
                    var grant = inventoryService.addItemWithResult(playerId, itemKey, qty);
                    if (grant.quantityGranted() != qty) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                                "背包堆叠空间不足或奖励物品不可用，处理后再领取广播奖励");
                    }
                    recordBroadcastResourceChange(playerId, "item", itemKey,
                            grant.totalQuantity() - grant.quantityGranted(), grant.totalQuantity(),
                            eventKey, transactionGroupId, resourceMetadata);
                    Map<String, Object> acquired = new LinkedHashMap<>();
                    acquired.put("itemKey", itemKey);
                    acquired.put("quantity", grant.quantityGranted());
                    grantedItems.add(acquired);
                }
            }
            personalRewards.put("items", grantedItems);
        }

        // Mark claimed
        contribution.setRewardClaimed(1);
        contribution.setUpdatedAt(LocalDateTime.now());
        if (contributionMapper.updateById(contribution) <= 0) {
            throw new IllegalStateException("广播奖励领取状态保存失败");
        }

        // Return rewards
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventKey", eventKey);
        result.put("claimed", true);
        result.put("rewards", personalRewards);
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public int contributeToActiveAiEvents(Long playerId, String objectiveType, int units) {
        if (playerId == null || units <= 0 || !AI_OBJECTIVE_TYPES.contains(objectiveType)) return 0;

        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<BroadcastEvent> activeQuery = new QueryWrapper<>();
        activeQuery.eq("status", "active")
                .and(q -> q.isNull("start_at").or().le("start_at", now))
                .and(q -> q.isNull("end_at").or().gt("end_at", now))
                .orderByAsc("id");
        List<BroadcastEvent> candidates = eventMapper.selectList(activeQuery);
        if (candidates == null || candidates.isEmpty()) return 0;

        int advancedEvents = 0;
        boolean completedAny = false;
        for (BroadcastEvent candidate : candidates) {
            if (candidate.getEventKey() == null || !candidate.getEventKey().startsWith("ai_")) continue;

            // Lock each matching event in stable ID order so parallel actions cannot overwrite JSON progress.
            QueryWrapper<BroadcastEvent> lockQuery = new QueryWrapper<>();
            lockQuery.eq("event_key", candidate.getEventKey())
                    .eq("status", "active")
                    .and(q -> q.isNull("start_at").or().le("start_at", now))
                    .and(q -> q.isNull("end_at").or().gt("end_at", now))
                    .last("FOR UPDATE");
            BroadcastEvent event = eventMapper.selectOne(lockQuery);
            if (event == null) continue;

            Map<String, Object> metadata = parseJsonMap(event.getMetadataJson());
            Object rawObjectives = metadata.get("objectives");
            if (!(rawObjectives instanceof List<?> objectives) || objectives.isEmpty()) continue;

            int totalProgress = 0;
            int totalTarget = 0;
            int contributionScore = 0;
            boolean changed = false;
            for (Object rawObjective : objectives) {
                if (!(rawObjective instanceof Map<?, ?> rawMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> objective = (Map<String, Object>) rawMap;
                int target = toInt(objective.get("target"), 0);
                int current = Math.max(0, Math.min(target, toInt(objective.get("current"), 0)));
                if (target > 0 && objectiveType.equals(objective.get("type")) && current < target) {
                    int acceptedUnits = Math.min(units, target - current);
                    current += acceptedUnits;
                    int scorePerUnit = Math.max(1, Math.min(1000,
                            toInt(objective.get("score_per_unit"), 1)));
                    contributionScore = Math.addExact(contributionScore,
                            Math.multiplyExact(acceptedUnits, scorePerUnit));
                    objective.put("current", current);
                    changed = true;
                }
                if (target > 0) {
                    objective.put("current", current);
                    totalTarget = Math.addExact(totalTarget, target);
                    totalProgress = Math.addExact(totalProgress, current);
                }
            }
            if (!changed) continue;

            event.setCurrentValue(Math.min(totalProgress, totalTarget));
            event.setMetadataJson(writeJson(metadata, "AI 放送目标进度保存失败"));
            event.setUpdatedAt(now);
            if (eventMapper.updateById(event) <= 0) {
                throw new IllegalStateException("AI 放送进度写入失败: " + event.getEventKey());
            }
            recordAiContribution(event.getEventKey(), playerId, contributionScore, objectiveType, now);
            advancedEvents++;

            if (totalTarget > 0 && totalProgress >= totalTarget) {
                completeEvent(event);
                completedAny = true;
            }
        }

        if (advancedEvents > 0 && !completedAny) afterCommit(this::pushBroadcastUpdate);
        return advancedEvents;
    }

    private void recordAiContribution(String eventKey, Long playerId, int value,
                                      String contributionType, LocalDateTime now) {
        if (value <= 0) return;
        QueryWrapper<BroadcastContribution> query = new QueryWrapper<>();
        query.eq("event_key", eventKey).eq("player_id", playerId);
        BroadcastContribution existing = contributionMapper.selectOne(query);
        if (existing == null) {
            BroadcastContribution contribution = new BroadcastContribution();
            contribution.setEventKey(eventKey);
            contribution.setPlayerId(playerId);
            contribution.setContributionValue(value);
            contribution.setContributionType(contributionType);
            contribution.setRewardClaimed(0);
            contribution.setLastContributedAt(now);
            contribution.setCreatedAt(now);
            contribution.setUpdatedAt(now);
            if (contributionMapper.insert(contribution) <= 0) {
                throw new IllegalStateException("AI 放送个人贡献创建失败: " + eventKey);
            }
            return;
        }

        existing.setContributionValue(Math.addExact(existing.getContributionValue(), value));
        existing.setLastContributedAt(now);
        existing.setUpdatedAt(now);
        if (contributionMapper.updateById(existing) <= 0) {
            throw new IllegalStateException("AI 放送个人贡献写入失败: " + eventKey);
        }
    }

    private void recordBroadcastResourceChange(Long playerId, String resourceType, String resourceKey,
                                               long before, long after, String eventKey,
                                               String transactionGroupId, Map<String, Object> metadata) {
        long amount = after - before;
        if (amount == 0) return;
        String idempotencyKey = transactionGroupId + ":" + resourceType + ":" + resourceKey;
        boolean inserted = resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, before, after,
                "broadcast_reward", eventKey, transactionGroupId, idempotencyKey, metadata));
        if (!inserted) throw new IllegalStateException("星流放送奖励流水已存在，拒绝重复结算: " + eventKey);
    }

    @Override
    public List<Map<String, Object>> getPlayerContributions(Long playerId) {
        QueryWrapper<BroadcastContribution> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId)
          .orderByDesc("updated_at");
        List<BroadcastContribution> list = contributionMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (BroadcastContribution bc : list) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventKey", bc.getEventKey());
            entry.put("contributionValue", bc.getContributionValue());
            entry.put("contributionType", bc.getContributionType());
            entry.put("rewardClaimed", bc.getRewardClaimed());

            BroadcastEvent event = getEventByKeySafe(bc.getEventKey());
            if (event != null) {
                entry.put("eventTitle", event.getTitle());
                entry.put("eventStatus", event.getStatus());
                entry.put("targetValue", event.getTargetValue());
                entry.put("currentValue", event.getCurrentValue());
            }
            result.add(entry);
        }
        return result;
    }

    @Override
    public BroadcastSummaryVO getBroadcastSummary() {
        LocalDateTime now = LocalDateTime.now();
        // Active event count
        QueryWrapper<BroadcastEvent> aq = new QueryWrapper<>();
        aq.eq("status", "active")
          .and(q -> q.isNull("start_at").or().le("start_at", now))
          .and(q -> q.isNull("end_at").or().gt("end_at", now));
        long activeCount = eventMapper.selectCount(aq);

        // Total unique contributors
        QueryWrapper<BroadcastContribution> cq = new QueryWrapper<>();
        cq.select("DISTINCT player_id");
        long totalContributors = contributionMapper.selectCount(cq);

        // Top events by progress
        QueryWrapper<BroadcastEvent> tq = new QueryWrapper<>();
        tq.and(q -> q.eq("status", "completed")
                .or(active -> active.eq("status", "active")
                        .and(start -> start.isNull("start_at").or().le("start_at", now))
                        .and(end -> end.isNull("end_at").or().gt("end_at", now))))
          .orderByDesc("current_value")
          .last("LIMIT 5");
        List<BroadcastEvent> topEvents = eventMapper.selectList(tq);

        List<Map<String, Object>> topList = new ArrayList<>();
        for (BroadcastEvent e : topEvents) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventKey", e.getEventKey());
            item.put("title", e.getTitle());
            item.put("type", e.getType());
            item.put("status", e.getStatus());
            item.put("currentValue", e.getCurrentValue());
            item.put("targetValue", e.getTargetValue());
            double pct = e.getTargetValue() > 0
                    ? (double) e.getCurrentValue() / e.getTargetValue() * 100.0
                    : 0;
            item.put("progressPercent", Math.round(pct * 10.0) / 10.0);
            topList.add(item);
        }

        BroadcastSummaryVO vo = new BroadcastSummaryVO();
        vo.setActiveCount((int) activeCount);
        vo.setTotalContributors((int) totalContributors);
        vo.setTopEvents(topList);
        return vo;
    }

    @Override
    public List<Map<String, Object>> getLeaderboard(String eventKey, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        QueryWrapper<BroadcastContribution> qw = new QueryWrapper<>();
        if (eventKey != null && !eventKey.isBlank()) {
            // Each contribution row is already unique per event/player; rank only this event.
            qw.select("player_id", "contribution_value AS total_contribution")
              .eq("event_key", eventKey)
              .orderByDesc("contribution_value")
              .orderByAsc("player_id");
        } else {
            // Keep the old unscoped endpoint behavior for existing external clients.
            qw.select("player_id", "SUM(contribution_value) AS total_contribution")
              .groupBy("player_id")
              .orderByDesc("total_contribution")
              .orderByAsc("player_id");
        }
        qw.last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        List<Map<String, Object>> aggList = contributionMapper.selectMaps(qw);

        int rowNumber = 0;
        int rank = 0;
        Long previousContribution = null;
        for (Map<String, Object> row : aggList) {
            rowNumber++;
            Long playerId = toLong(row.get("player_id"));
            Long totalContribution = toLong(row.get("total_contribution"));
            if (previousContribution == null || !previousContribution.equals(totalContribution)) {
                rank = rowNumber;
            }
            Player player = playerMapper.selectById(playerId);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", rank);
            entry.put("player_id", playerId);
            entry.put("player_name", player != null ? player.getPlayerName() : "未知玩家");
            entry.put("level", player != null ? extractLevel(player) : 1);
            entry.put("total_contribution", totalContribution);
            if (eventKey != null && !eventKey.isBlank()) entry.put("event_key", eventKey);
            result.add(entry);
            previousContribution = totalContribution;
        }
        return result;
    }

    @Override
    public Map<String, Object> getPlayerLeaderboardPosition(String eventKey, Long playerId) {
        getEventByKey(eventKey);

        QueryWrapper<BroadcastContribution> mineQuery = new QueryWrapper<>();
        mineQuery.eq("event_key", eventKey).eq("player_id", playerId);
        BroadcastContribution mine = contributionMapper.selectOne(mineQuery);

        QueryWrapper<BroadcastContribution> participantCountQuery = new QueryWrapper<>();
        participantCountQuery.eq("event_key", eventKey);
        long participantCount = contributionMapper.selectCount(participantCountQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventKey", eventKey);
        result.put("playerId", playerId);
        result.put("participated", mine != null);
        result.put("participantCount", participantCount);
        if (mine == null) {
            result.put("rank", null);
            result.put("contribution", 0L);
            result.put("pointsToOvertakeNext", null);
            return result;
        }

        long contribution = toLong(mine.getContributionValue());
        QueryWrapper<BroadcastContribution> betterCountQuery = new QueryWrapper<>();
        betterCountQuery.eq("event_key", eventKey).gt("contribution_value", contribution);
        long betterCount = contributionMapper.selectCount(betterCountQuery);

        QueryWrapper<BroadcastContribution> nextRankQuery = new QueryWrapper<>();
        nextRankQuery.select("contribution_value")
                .eq("event_key", eventKey)
                .gt("contribution_value", contribution)
                .orderByAsc("contribution_value")
                .last("LIMIT 1");
        BroadcastContribution nextRank = contributionMapper.selectOne(nextRankQuery);

        result.put("rank", betterCount + 1L);
        result.put("contribution", contribution);
        result.put("pointsToOvertakeNext", nextRank == null
                ? 0L : Math.max(0L, toLong(nextRank.getContributionValue()) - contribution + 1L));
        return result;
    }

    private long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private int extractLevel(Player player) {
        if (player.getStatsJson() == null) return 1;
        try {
            Map<String, Object> stats = objectMapper.readValue(player.getStatsJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
            Object lv = stats.get("level");
            return lv instanceof Number ? ((Number) lv).intValue() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    // ─── internal ───

    @Override
    @Transactional
    public String createEvent(Map<String, Object> draft) {
        if (draft == null || draft.isEmpty()) return null;

        String eventType = String.valueOf(draft.getOrDefault("eventType", "opportunity_rain"));
        String title = String.valueOf(draft.getOrDefault("title", "星流放送"));
        String description = String.valueOf(draft.getOrDefault("description", ""));
        int durationMinutes = Math.max(15, Math.min(180, toInt(draft.get("durationMinutes"), 60)));

        List<Map<String, Object>> objectives = normalizeObjectives(draft.get("objectives"));
        if (objectives.isEmpty()) {
            log.warn("Refusing to create AI broadcast without supported, actionable objectives");
            return null;
        }
        int targetValue = objectives.stream().mapToInt(o -> toInt(o.get("target"), 0)).sum();

        // Build event key
        String eventKey = "ai_" + System.currentTimeMillis();

        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey(eventKey);
        event.setTitle(title);
        event.setDescription(description);
        event.setType(eventType);
        event.setStatus("active");
        event.setTargetValue(targetValue);
        event.setCurrentValue(0);
        event.setStartAt(LocalDateTime.now());
        event.setEndAt(LocalDateTime.now().plusMinutes(durationMinutes));
        event.setCreatedBy(null);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());

        // Serialize JSON fields
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("objectives", objectives);
        metadata.put("reason", Objects.toString(draft.getOrDefault("reason", ""), ""));
        metadata.put("generatedBy", Objects.toString(draft.getOrDefault("generatedBy", "unknown"), "unknown"));
        metadata.put("worldStateSnapshot", draft.get("worldStateSnapshot"));
        try {
            event.setRewardsJson(writeJson(asMap(draft.get("rewards")), "广播奖励序列化失败"));
            event.setConditionsJson(writeJson(asMap(draft.get("requirements")), "广播条件序列化失败"));
            event.setWorldlineEffectsJson(writeJson(asMap(draft.get("failurePenalty")), "广播效果序列化失败"));
            event.setMetadataJson(writeJson(metadata, "广播目标序列化失败"));
        } catch (IllegalStateException e) {
            log.warn("Failed to serialize AI broadcast draft: {}", e.getMessage());
            return null;
        }

        eventMapper.insert(event);

        // Push real-time update to all online players
        pushBroadcastUpdate();

        // Send system chat notification
        try {
            chatService.saveSystemMessage(
                    "星之流发布新放送「" + title + "」，持续 " + durationMinutes + " 分钟。",
                    "broadcast", Map.of("eventKey", eventKey, "eventTitle", title));
        } catch (Exception ignored) {}

        return eventKey;
    }

    private List<Map<String, Object>> normalizeObjectives(Object rawObjectives) {
        if (!(rawObjectives instanceof List<?> values) || values.isEmpty() || values.size() > AI_OBJECTIVE_TYPES.size()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> rawMap)) return List.of();
            Object rawType = rawMap.get("type");
            String type = rawType instanceof String ? ((String) rawType).trim() : "";
            int target = toInt(rawMap.get("target"), 0);
            Object rawLabel = rawMap.get("label");
            String label = rawLabel instanceof String ? ((String) rawLabel).trim() : "";
            if (!AI_OBJECTIVE_TYPES.contains(type) || !seenTypes.add(type)
                    || target < 1 || label.isBlank()) {
                return List.of();
            }

            Map<String, Object> objective = new LinkedHashMap<>();
            objective.put("type", type);
            objective.put("target", Math.min(target, 10_000));
            objective.put("label", label.length() <= 200 ? label : label.substring(0, 200));
            objective.put("score_per_unit", Math.max(1, Math.min(1000,
                    toInt(rawMap.get("score_per_unit"), 1))));
            objective.put("current", 0);
            normalized.add(objective);
        }
        return normalized;
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, item) -> {
            if (key instanceof String stringKey) result.put(stringKey, item);
        });
        return result;
    }

    private boolean hasClaimableReward(Map<String, Object> rewards) {
        if (toInt(rewards.get("coins"), 0) > 0
                || toInt(rewards.get("storyFragments"), 0) > 0
                || toInt(rewards.get("channelHeat"), 0) > 0) {
            return true;
        }
        Object rawItems = rewards.get("items");
        if (!(rawItems instanceof List<?> items)) return false;
        return items.stream().anyMatch(rawItem -> {
            if (!(rawItem instanceof Map<?, ?> item)) return false;
            Object itemKey = item.get("itemKey");
            return itemKey instanceof String key && !key.isBlank()
                    && toInt(item.get("quantity"), 1) > 0;
        });
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private BroadcastEvent getEventByKey(String eventKey) {
        QueryWrapper<BroadcastEvent> qw = new QueryWrapper<>();
        qw.eq("event_key", eventKey);
        BroadcastEvent event = eventMapper.selectOne(qw);
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "广播事件不存在: " + eventKey);
        }
        return event;
    }

    private BroadcastEvent getEventByKeySafe(String eventKey) {
        QueryWrapper<BroadcastEvent> qw = new QueryWrapper<>();
        qw.eq("event_key", eventKey);
        return eventMapper.selectOne(qw);
    }

    private void completeEvent(BroadcastEvent event) {
        event.setStatus("completed");
        event.setUpdatedAt(LocalDateTime.now());
        if (eventMapper.updateById(event) <= 0) {
            throw new IllegalStateException("广播事件完成状态写入失败: " + event.getEventKey());
        }

        // Apply worldline effects
        String effectsJson = event.getWorldlineEffectsJson();
        if (effectsJson != null && !effectsJson.isBlank()) {
            worldlineService.applyBroadcastWorldlineEffects(event.getEventKey(), effectsJson);
        }

        // Don't tell clients the event completed until its database changes are durable.
        afterCommit(() -> {
            pushBroadcastUpdate();
            try {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("eventKey", event.getEventKey());
                metadata.put("eventTitle", event.getTitle());
                chatService.saveSystemMessage(
                        buildCompletionMessage(event),
                        "broadcast", metadata);
            } catch (Exception e) {
                log.warn("Failed to publish broadcast completion message for {}", event.getEventKey(), e);
            }
        });
    }

    private void afterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
        } else {
            callback.run();
        }
    }

    private String buildCompletionMessage(BroadcastEvent event) {
        String title = event.getTitle() == null || event.getTitle().isBlank()
                ? "星流放送" : event.getTitle();
        StringBuilder message = new StringBuilder("星流放送「")
                .append(title)
                .append("」已完成。全服目标：")
                .append(buildCompletedObjectiveSummary(event))
                .append("。");

        List<String> publicEffects = buildWorldlineEffectSummary(event.getWorldlineEffectsJson());
        if (publicEffects.isEmpty()) {
            message.append("本次放送未配置直接世界线数值变化。");
        } else {
            message.append("公共世界变化：")
                    .append(String.join("、", publicEffects))
                    .append("。");
        }
        return message.toString();
    }

    private String buildCompletedObjectiveSummary(BroadcastEvent event) {
        Map<String, Object> metadata = parseJsonMap(event.getMetadataJson());
        Object rawObjectives = metadata.get("objectives");
        if (rawObjectives instanceof List<?> objectives) {
            List<String> summaries = new ArrayList<>();
            for (Object rawObjective : objectives) {
                if (!(rawObjective instanceof Map<?, ?> objective)) continue;
                String type = Objects.toString(objective.get("type"), "");
                String label = Objects.toString(objective.get("label"), "").trim();
                if (label.isEmpty()) label = AI_OBJECTIVE_LABELS.getOrDefault(type, "全服目标");
                int target = toInt(objective.get("target"), 0);
                if (target <= 0) continue;
                int current = Math.max(0, Math.min(target, toInt(objective.get("current"), target)));
                summaries.add(label + " " + current + "/" + target);
            }
            if (!summaries.isEmpty()) return String.join("、", summaries);
        }

        int target = event.getTargetValue() == null ? 0 : Math.max(0, event.getTargetValue());
        int current = event.getCurrentValue() == null ? target : Math.max(0, event.getCurrentValue());
        return target > 0 ? "全服累计进度 " + Math.min(current, target) + "/" + target : "全服目标已达成";
    }

    private List<String> buildWorldlineEffectSummary(String effectsJson) {
        Map<String, Object> effects = parseJsonMap(effectsJson);
        List<String> summaries = new ArrayList<>();
        for (Map.Entry<String, String> effect : WORLDLINE_EFFECT_LABELS) {
            Object rawValue = effects.get(effect.getKey());
            if (!(rawValue instanceof Number number)) continue;
            double value = number.doubleValue();
            if (!Double.isFinite(value) || Math.abs(value) < 0.000001) continue;
            String formatted = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            if (value > 0) formatted = "+" + formatted;
            summaries.add(effect.getValue() + " " + formatted);
        }
        return summaries;
    }

    private void pushBroadcastUpdate() {
        try {
            BroadcastSummaryVO summary = getBroadcastSummary();
            List<Map<String, Object>> activeEvents = new ArrayList<>();
            for (BroadcastEventVO vo : getActiveBroadcasts()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("eventKey", vo.getEventKey());
                item.put("title", vo.getTitle());
                item.put("type", vo.getType());
                item.put("status", vo.getStatus());
                item.put("currentValue", vo.getCurrentValue());
                item.put("targetValue", vo.getTargetValue());
                item.put("objectives", vo.getProgress().get("objectives"));
                activeEvents.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activeCount", summary.getActiveCount());
            data.put("totalContributors", summary.getTotalContributors());
            data.put("topEvents", summary.getTopEvents());
            data.put("activeEvents", activeEvents);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "broadcast.progress.updated");
            msg.put("data", data);
            sessionManager.broadcastToAll(msg);
        } catch (Exception e) {
            log.warn("Failed to push broadcast update: {}", e.getMessage());
        }
    }

    private BroadcastEventVO toVO(BroadcastEvent e) {
        BroadcastEventVO vo = new BroadcastEventVO();
        vo.setEventKey(e.getEventKey());
        vo.setTitle(e.getTitle());
        vo.setDescription(e.getDescription());
        vo.setType(e.getType());
        vo.setStatus(e.getStatus());
        vo.setTargetValue(e.getTargetValue());
        vo.setCurrentValue(e.getCurrentValue());
        vo.setStartAt(e.getStartAt() != null ? e.getStartAt().toString() : null);
        vo.setEndAt(e.getEndAt() != null ? e.getEndAt().toString() : null);
        vo.setRewards(parseJsonMap(e.getRewardsJson()));
        vo.setPersonalRewards(parseJsonMap(e.getPersonalRewardsJson()));
        vo.setWorldlineEffects(parseJsonMap(e.getWorldlineEffectsJson()));
        vo.setConditions(parseJsonMap(e.getConditionsJson()));
        vo.setMetadata(parseJsonMap(e.getMetadataJson()));
        vo.setCreatedBy(e.getCreatedBy() != null ? String.valueOf(e.getCreatedBy()) : null);
        vo.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return vo;
    }

    private boolean isWithinActiveWindow(BroadcastEvent event, LocalDateTime now) {
        return event != null
                && (event.getStartAt() == null || !event.getStartAt().isAfter(now))
                && (event.getEndAt() == null || event.getEndAt().isAfter(now));
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
