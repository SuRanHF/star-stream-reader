package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.BroadcastContribution;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.BroadcastContributionMapper;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.BroadcastEventVO;
import com.huazhenghai.readergame.vo.BroadcastSummaryVO;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BroadcastServiceImpl implements BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastServiceImpl.class);

    private final BroadcastEventMapper eventMapper;
    private final BroadcastContributionMapper contributionMapper;
    private final WorldlineService worldlineService;
    private final ChatService chatService;
    private final PlayerMapper playerMapper;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;

    public BroadcastServiceImpl(BroadcastEventMapper eventMapper,
                                BroadcastContributionMapper contributionMapper,
                                WorldlineService worldlineService,
                                ChatService chatService,
                                PlayerMapper playerMapper,
                                InventoryService inventoryService,
                                ObjectMapper objectMapper,
                                WebSocketSessionManager sessionManager) {
        this.eventMapper = eventMapper;
        this.contributionMapper = contributionMapper;
        this.worldlineService = worldlineService;
        this.chatService = chatService;
        this.playerMapper = playerMapper;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
    }

    @Override
    public List<BroadcastEventVO> getActiveBroadcasts() {
        QueryWrapper<BroadcastEvent> qw = new QueryWrapper<>();
        qw.eq("status", "active")
          .orderByDesc("created_at");
        List<BroadcastEvent> events = eventMapper.selectList(qw);

        List<BroadcastEventVO> result = new ArrayList<>();
        for (BroadcastEvent e : events) {
            result.add(toVO(e));
        }
        return result;
    }

    @Override
    public BroadcastEventVO getBroadcastDetail(String eventKey) {
        BroadcastEvent event = getEventByKey(eventKey);
        return toVO(event);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> contribute(String eventKey, Long playerId, int value, String contributionType) {
        BroadcastEvent event = getEventByKey(eventKey);

        if (!"active".equals(event.getStatus())) {
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
            contributionMapper.updateById(existing);
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
            contributionMapper.insert(bc);
        }

        // Update event current_value
        UpdateWrapper<BroadcastEvent> uw = new UpdateWrapper<>();
        uw.eq("event_key", eventKey)
          .setSql("current_value = current_value + " + value);
        eventMapper.update(null, uw);

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
        BroadcastEvent event = getEventByKey(eventKey);

        // Find player's contribution
        QueryWrapper<BroadcastContribution> cq = new QueryWrapper<>();
        cq.eq("event_key", eventKey)
          .eq("player_id", playerId);
        BroadcastContribution contribution = contributionMapper.selectOne(cq);

        if (contribution == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "你未参与此广播事件");
        }

        if (contribution.getRewardClaimed() == 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你已经领取过此奖励");
        }

        // Parse personal rewards to check min contribution
        Map<String, Object> personalRewards = parseJsonMap(event.getPersonalRewardsJson());
        int minContribution = toInt(personalRewards.get("minContribution"), 0);

        if (contribution.getContributionValue() < minContribution) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    "贡献值不足，需要至少" + minContribution + "点贡献");
        }

        // Grant actual rewards to player
        Player player = playerMapper.selectById(playerId);
        if (player != null) {
            int coins = toInt(personalRewards.get("coins"), 0);
            if (coins > 0) {
                player.setCoins(player.getCoins() + coins);
            }
            int fragments = toInt(personalRewards.get("storyFragments"), 0);
            if (fragments > 0) {
                player.setStoryFragments(player.getStoryFragments() + fragments);
            }
            int heat = toInt(personalRewards.get("channelHeat"), 0);
            if (heat > 0) {
                Map<String, Object> stats = parseJsonMap(player.getStatsJson());
                int currentHeat = toInt(stats.get("channelHeat"), 0);
                stats.put("channelHeat", currentHeat + heat);
                try { player.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
            }
            playerMapper.updateById(player);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) personalRewards.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    String itemKey = (String) item.get("itemKey");
                    int qty = toInt(item.get("quantity"), 1);
                    try {
                        if (itemKey != null && qty > 0) inventoryService.addItem(playerId, itemKey, qty);
                    } catch (Exception e) {
                        log.warn("Broadcast reward item grant failed: player={}, itemKey={}", playerId, itemKey, e);
                    }
                }
            }
        }

        // Mark claimed
        contribution.setRewardClaimed(1);
        contribution.setUpdatedAt(LocalDateTime.now());
        contributionMapper.updateById(contribution);

        // Return rewards
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventKey", eventKey);
        result.put("claimed", true);
        result.put("rewards", personalRewards);
        return result;
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
        // Active event count
        QueryWrapper<BroadcastEvent> aq = new QueryWrapper<>();
        aq.eq("status", "active");
        long activeCount = eventMapper.selectCount(aq);

        // Total unique contributors
        QueryWrapper<BroadcastContribution> cq = new QueryWrapper<>();
        cq.select("DISTINCT player_id");
        long totalContributors = contributionMapper.selectCount(cq);

        // Top events by progress
        QueryWrapper<BroadcastEvent> tq = new QueryWrapper<>();
        tq.in("status", List.of("active", "completed"))
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
    public List<Map<String, Object>> getLeaderboard(int limit) {
        // 聚合所有玩家贡献值，按 total_contribution 降序
        List<Map<String, Object>> result = new ArrayList<>();
        QueryWrapper<BroadcastContribution> qw = new QueryWrapper<>();
        qw.select("player_id", "SUM(contribution_value) AS total_contribution")
          .groupBy("player_id")
          .orderByDesc("total_contribution")
          .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        List<Map<String, Object>> aggList = contributionMapper.selectMaps(qw);

        int rank = 1;
        for (Map<String, Object> row : aggList) {
            Long playerId = toLong(row.get("player_id"));
            Long totalContribution = toLong(row.get("total_contribution"));
            Player player = playerMapper.selectById(playerId);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", rank++);
            entry.put("player_id", playerId);
            entry.put("player_name", player != null ? player.getPlayerName() : "未知玩家");
            entry.put("level", player != null ? extractLevel(player) : 1);
            entry.put("total_contribution", totalContribution);
            result.add(entry);
        }
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
        int durationMinutes = toInt(draft.get("durationMinutes"), 60);

        // Compute targetValue from objectives
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> objectives = (List<Map<String, Object>>) draft.getOrDefault("objectives", List.of());
        int targetValue = objectives.stream().mapToInt(o -> toInt(o.get("target"), 1)).sum();

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
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> rewards = (Map<String, Object>) draft.getOrDefault("rewards", Map.of());
            event.setRewardsJson(objectMapper.writeValueAsString(rewards));

            @SuppressWarnings("unchecked")
            Map<String, Object> requirements = (Map<String, Object>) draft.getOrDefault("requirements", Map.of());
            event.setConditionsJson(objectMapper.writeValueAsString(requirements));

            @SuppressWarnings("unchecked")
            Map<String, Object> failurePenalty = (Map<String, Object>) draft.getOrDefault("failurePenalty", Map.of());
            event.setWorldlineEffectsJson(objectMapper.writeValueAsString(failurePenalty));

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("objectives", objectives);
            metadata.put("reason", draft.getOrDefault("reason", ""));
            metadata.put("generatedBy", draft.getOrDefault("generatedBy", "unknown"));
            metadata.put("worldStateSnapshot", draft.get("worldStateSnapshot"));
            event.setMetadataJson(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            log.warn("Failed to serialize broadcast event JSON fields: {}", e.getMessage());
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
        eventMapper.updateById(event);

        // Push real-time update to all online players
        pushBroadcastUpdate();

        // Apply worldline effects
        String effectsJson = event.getWorldlineEffectsJson();
        if (effectsJson != null && !effectsJson.isBlank()) {
            worldlineService.applyBroadcastWorldlineEffects(event.getEventKey(), effectsJson);
        }

        // Send system chat message (non-critical)
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("eventKey", event.getEventKey());
            metadata.put("eventTitle", event.getTitle());
            chatService.saveSystemMessage(
                    "星流放送「" + event.getTitle() + "」已完成，世界线发生轻微偏移。",
                    "broadcast", metadata);
        } catch (Exception e) {
            // Don't let chat failure affect broadcast completion
        }
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
