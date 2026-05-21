package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.ExplorationEventMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.QuestService;
import org.springframework.transaction.annotation.Transactional;
import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExploreServiceImpl implements ExploreService {

    private final PlayerMapper playerMapper;
    private final LocationMapper locationMapper;
    private final ExplorationEventMapper eventMapper;
    private final RecoveryService recoveryService;
    private final PlayerLogService playerLogService;
    private final TitleService titleService;
    private final InventoryService inventoryService;
    private final EquipmentService equipmentService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final ObjectMapper objectMapper;

    public ExploreServiceImpl(PlayerMapper playerMapper,
                              LocationMapper locationMapper,
                              ExplorationEventMapper eventMapper,
                              RecoveryService recoveryService,
                              PlayerLogService playerLogService,
                              TitleService titleService,
                              InventoryService inventoryService,
                              EquipmentService equipmentService,
                              BroadcastService broadcastService,
                              FactionService factionService,
                              QuestService questService,
                              ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.locationMapper = locationMapper;
        this.eventMapper = eventMapper;
        this.recoveryService = recoveryService;
        this.playerLogService = playerLogService;
        this.titleService = titleService;
        this.inventoryService = inventoryService;
        this.equipmentService = equipmentService;
        this.broadcastService = broadcastService;
        this.factionService = factionService;
        this.questService = questService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Map<String, Object> startExplore(Long playerId, String locationKey, Long userId) {
        // 1. 校验玩家
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        // 2. 应用恢复
        Map<String, Object> stats = recoveryService.applyRecovery(player);

        // 3. 检查休息状态
        recoveryService.assertCanAct(player);

        // 4. 确定地点
        if (locationKey == null || locationKey.isBlank()) {
            locationKey = player.getCurrentLocation();
        }
        if (locationKey == null || locationKey.isBlank()) {
            locationKey = "ruined_station";
        }

        QueryWrapper<Location> locQuery = new QueryWrapper<>();
        locQuery.eq("location_key", locationKey);
        Location location = locationMapper.selectOne(locQuery);
        if (location == null)
            throw new BusinessException(ErrorCode.LOCATION_NOT_FOUND, "该地点不存在");

        // 5. 检查解锁条件
        Map<String, Object> unlockConditions = parseJsonMap(location.getUnlockConditionsJson());
        if (!checkUnlock(stats, unlockConditions))
            throw new BusinessException(ErrorCode.LOCATION_LOCKED, "该地点尚未解锁");

        // 6. 获取可用事件
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        int storyPity = toInt(stageProgress.get("storyPity"), 0);

        QueryWrapper<ExplorationEvent> evQuery = new QueryWrapper<>();
        evQuery.eq("location_key", locationKey)
               .eq("enabled", 1);
        List<ExplorationEvent> events = eventMapper.selectList(evQuery);

        if (events.isEmpty())
            throw new BusinessException(ErrorCode.NO_AVAILABLE_EVENT, "该地点暂无可用的探索事件");

        // 7. 按权重抽取事件 (含 storyPity 保底)
        ExplorationEvent selected = selectEvent(events, storyPity);

        // 7a. battle_placeholder: 返回战斗信息，不在此扣体力/发奖励
        if ("battle_placeholder".equals(selected.getEventType())) {
            player.setCurrentLocation(locationKey);
            try {
                player.setStatsJson(objectMapper.writeValueAsString(stats));
                player.setStageProgressJson(objectMapper.writeValueAsString(stageProgress));
            } catch (Exception ignored) {}
            playerMapper.updateById(player);

            Map<String, Object> battleResult = new LinkedHashMap<>();
            battleResult.put("result_type", "battle");
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("event_key", selected.getEventKey());
            eventData.put("event_type", selected.getEventType());
            eventData.put("name", selected.getName());
            eventData.put("description", selected.getDescription());
            battleResult.put("result", eventData);

            Map<String, Object> risks = parseJsonMap(selected.getRisksJson());
            String monsterKey = (String) risks.get("monsterKey");
            battleResult.put("monster_key", monsterKey != null ? monsterKey : "");

            battleResult.put("rewards", new LinkedHashMap<>());
            battleResult.put("progress_effects", new LinkedHashMap<>());
            battleResult.put("new_titles", new ArrayList<>());
            battleResult.put("player", buildPlayerSnapshot(player, stats, stageProgress));
            battleResult.put("new_logs", new ArrayList<>());
            return battleResult;
        }

        // 8. 检查体力
        int stamina = toInt(stats.get("stamina"), 50);
        if (stamina < selected.getStaminaCost())
            throw new BusinessException(ErrorCode.STAMINA_NOT_ENOUGH,
                    "体力不足，需要" + selected.getStaminaCost() + "点体力");

        // 9. 扣除体力
        stats.put("stamina", stamina - selected.getStaminaCost());
        // 更新当前地点
        player.setCurrentLocation(locationKey);

        // 10. 应用奖励
        Map<String, Object> appliedRewards = applyRewards(player, stats, selected.getRewardsJson());

        // 11. 应用进度效果
        Map<String, Object> appliedProgress = applyProgressEffects(stageProgress, selected);

        // 12. 写日志
        String logMsg = selected.getLogTemplate();
        if (logMsg == null || logMsg.isBlank()) {
            logMsg = selected.getName();
        }
        String logType = mapEventTypeToLogType(selected.getEventType());
        playerLogService.addLog(playerId, logType, logMsg);

        // 13. 保存玩家状态
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
            player.setStageProgressJson(objectMapper.writeValueAsString(stageProgress));
        } catch (Exception ignored) {
        }
        playerMapper.updateById(player);

        // 14. 评估称号解锁
        List<PlayerTitleVO> newTitles = titleService.evaluateAndUnlockTitles(playerId, null);

        // 14a. 自动贡献广播进度 (不阻断主流程)
        try {
            int contribValue = ("story".equals(selected.getEventType())
                    || "boss_clue".equals(selected.getEventType())) ? 2 : 1;
            String contribType = "story".equals(selected.getEventType()) ? "story" : "exploration";
            broadcastService.contribute("broadcast_station_cleanup", playerId, contribValue, contribType);
        } catch (Exception ignored) {
        }

        // 15. 构建返回结果 (snake_case keys 匹配前端)
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", selected.getEventType());

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("event_key", selected.getEventKey());
        eventData.put("event_type", selected.getEventType());
        eventData.put("name", selected.getName());
        eventData.put("description", selected.getDescription());
        result.put("result", eventData);

        result.put("rewards", appliedRewards);
        result.put("progress_effects", appliedProgress);
        result.put("new_titles", newTitles);

        // 玩家快照
        Map<String, Object> playerSnapshot = buildPlayerSnapshot(player, stats, stageProgress);
        result.put("player", playerSnapshot);

        // new_logs
        List<Map<String, Object>> newLogs = new ArrayList<>();
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("message", logMsg);
        logEntry.put("type", logType);
        newLogs.add(logEntry);
        result.put("new_logs", newLogs);

        // 自动阵营贡献
        try { factionService.contribute(playerId, null, "explore", 1L, "explore", selected.getEventKey(), null); }
        catch (Exception ignored) {}

        // 自动任务进度
        try { questService.addProgress(playerId, "explore_count", 1, "explore", selected.getEventKey()); }
        catch (Exception ignored) {}

        return result;
    }

    // ─── 事件选择 ───

    private ExplorationEvent selectEvent(List<ExplorationEvent> events, int storyPity) {
        // 保底: storyPity >= 8 → 优先选 story 事件
        if (storyPity >= 8) {
            for (ExplorationEvent e : events) {
                if ("story".equals(e.getEventType())) {
                    return e;
                }
            }
        }

        // storyPity 加成: 每层 +10% 权重 (5层就+50%)
        int totalWeight = 0;
        Map<String, Integer> adjustedWeights = new LinkedHashMap<>();
        for (ExplorationEvent e : events) {
            int w = e.getWeight();
            if ("story".equals(e.getEventType())) {
                w = w + storyPity * (w / 2); // 保底加成
            }
            adjustedWeights.put(e.getEventKey(), w);
            totalWeight += w;
        }

        if (totalWeight <= 0) return events.get(0);

        int roll = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (ExplorationEvent e : events) {
            cumulative += adjustedWeights.get(e.getEventKey());
            if (roll < cumulative) {
                return e;
            }
        }
        return events.get(events.size() - 1);
    }

    // ─── 奖励应用 ───

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyRewards(Player player, Map<String, Object> stats, String rewardsJson) {
        Map<String, Object> applied = new LinkedHashMap<>();
        Map<String, Object> rewards = parseJsonMap(rewardsJson);
        if (rewards.isEmpty()) return applied;

        // coins
        if (rewards.containsKey("coins")) {
            int add = toInt(rewards.get("coins"), 0);
            player.setCoins(player.getCoins() + add);
            applied.put("coins", add);
        }

        // storyFragments
        if (rewards.containsKey("storyFragments")) {
            int add = toInt(rewards.get("storyFragments"), 0);
            player.setStoryFragments(player.getStoryFragments() + add);
            applied.put("storyFragments", add);
        }

        // channelHeat (存储在 stats_json 中)
        if (rewards.containsKey("channelHeat")) {
            int add = toInt(rewards.get("channelHeat"), 0);
            int current = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", current + add);
            applied.put("channelHeat", add);
        }

        // worldLineShift (存储在 stats_json 中)
        if (rewards.containsKey("worldLineShift")) {
            int add = toInt(rewards.get("worldLineShift"), 0);
            int current = toInt(stats.get("worldLineShift"), 0);
            stats.put("worldLineShift", current + add);
            applied.put("worldLineShift", add);
        }

        // exp
        if (rewards.containsKey("exp")) {
            int add = toInt(rewards.get("exp"), 0);
            int current = toInt(stats.get("exp"), 0);
            stats.put("exp", current + add);
            applied.put("exp", add);
        }

        // items (物品掉落)
        if (rewards.containsKey("items")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) rewards.get("items");
            List<Map<String, Object>> acquiredItems = new ArrayList<>();
            for (Map<String, Object> itemDrop : items) {
                String itemKey = (String) itemDrop.get("itemKey");
                if (itemKey == null) continue;
                int qty = toInt(itemDrop.get("quantity"), 1);
                double dropRate = itemDrop.containsKey("dropRate") ?
                        ((Number) itemDrop.get("dropRate")).doubleValue() : 1.0;
                if (Math.random() < dropRate) {
                    int newQty = inventoryService.addItem(player.getId(), itemKey, qty);
                    Map<String, Object> acquired = new LinkedHashMap<>();
                    acquired.put("itemKey", itemKey);
                    acquired.put("quantity", qty);
                    acquired.put("totalQuantity", newQty);
                    acquiredItems.add(acquired);
                }
            }
            if (!acquiredItems.isEmpty()) {
                applied.put("items", acquiredItems);
            }
        }

        // equipment (装备掉落)
        if (rewards.containsKey("equipment")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> equipmentDrops = (List<Map<String, Object>>) rewards.get("equipment");
            List<Map<String, Object>> acquiredEquip = new ArrayList<>();
            for (Map<String, Object> drop : equipmentDrops) {
                String equipKey = (String) drop.get("equipmentKey");
                if (equipKey == null) continue;
                double dropRate = drop.containsKey("dropRate") ?
                        ((Number) drop.get("dropRate")).doubleValue() : 1.0;
                if (Math.random() < dropRate) {
                    int result = equipmentService.addEquipment(player.getId(), equipKey, "exploration");
                    if (result > 0) {
                        acquiredEquip.add(Map.of("equipmentKey", equipKey));
                    }
                }
            }
            if (!acquiredEquip.isEmpty()) {
                applied.put("equipment", acquiredEquip);
            }
        }

        return applied;
    }

    // ─── 进度效果 ───

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyProgressEffects(Map<String, Object> stageProgress, ExplorationEvent event) {
        Map<String, Object> applied = new LinkedHashMap<>();
        Map<String, Object> effects = parseJsonMap(event.getProgressEffectsJson());

        // storyPity: story类型清零, 其他类型+1
        if ("story".equals(event.getEventType())) {
            stageProgress.put("storyPity", 0);
            applied.put("storyPityReset", true);
        } else {
            int current = toInt(stageProgress.get("storyPity"), 0);
            stageProgress.put("storyPity", current + 1);
            applied.put("storyPity", current + 1);
        }

        // storyEventsTriggered
        if ("story".equals(event.getEventType())) {
            List<String> triggered = (List<String>) stageProgress.computeIfAbsent(
                    "storyEventsTriggered", k -> new ArrayList<>());
            if (!triggered.contains(event.getEventKey())) {
                triggered.add(event.getEventKey());
                applied.put("storyEventsTriggered", true);
            }
        } else {
            // 非story事件 → sideEventsTriggered
            List<String> sideTriggered = (List<String>) stageProgress.computeIfAbsent(
                    "sideEventsTriggered", k -> new ArrayList<>());
            if (!sideTriggered.contains(event.getEventKey())) {
                sideTriggered.add(event.getEventKey());
                applied.put("sideEventsTriggered", true);
            }
        }

        // bossClues
        if (effects.containsKey("bossClueKey")) {
            String bossKey = (String) effects.get("bossClueKey");
            int add = toInt(effects.get("bossClueAdd"), 1);
            Map<String, Object> bossClues = (Map<String, Object>) stageProgress.computeIfAbsent(
                    "bossClues", k -> new LinkedHashMap<>());
            int current = toInt(bossClues.get(bossKey), 0);
            bossClues.put(bossKey, current + add);
            applied.put("bossClueKey", bossKey);
            applied.put("bossClueCount", current + add);
        }

        // explorationsByLocation — 每次探索都记录
        Map<String, Object> byLoc = (Map<String, Object>) stageProgress.computeIfAbsent(
                "explorationsByLocation", k -> new LinkedHashMap<>());
        String locKey = event.getLocationKey() != null ? event.getLocationKey() : "unknown";
        int locCount = toInt(byLoc.get(locKey), 0);
        byLoc.put(locKey, locCount + 1);

        return applied;
    }

    // ─── 工具方法 ───

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

    private boolean checkUnlock(Map<String, Object> stats, Map<String, Object> conditions) {
        if (conditions.isEmpty()) return true;
        if (conditions.containsKey("required_level")) {
            int required = toInt(conditions.get("required_level"), 0);
            int level = toInt(stats.get("level"), 1);
            if (level < required) return false;
        }
        return true;
    }

    private String mapEventTypeToLogType(String eventType) {
        switch (eventType) {
            case "story":       return "story";
            case "resource":    return "exploration";
            case "opportunity": return "exploration";
            case "boss_clue":   return "milestone";
            case "empty":       return "info";
            default:            return "exploration";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPlayerSnapshot(Player player, Map<String, Object> stats,
                                                     Map<String, Object> stageProgress) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", player.getId());
        snapshot.put("player_name", player.getPlayerName());
        snapshot.put("current_chapter", player.getCurrentChapter());
        snapshot.put("coins", player.getCoins());
        snapshot.put("story_fragments", player.getStoryFragments());
        snapshot.put("user_id", player.getUserId());
        snapshot.put("current_main_chapter", player.getCurrentMainChapter());
        snapshot.put("current_location", player.getCurrentLocation());
        snapshot.put("current_location_name", player.getCurrentLocation());
        snapshot.put("stats", new LinkedHashMap<>(stats));
        snapshot.put("stage_progress", new LinkedHashMap<>(stageProgress));
        return snapshot;
    }
}
