package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerStoryLog;
import com.huazhenghai.readergame.mapper.ExplorationEventMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerStoryLogMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.QuestService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExploreServiceImpl implements ExploreService {

    private final PlayerMapper playerMapper;
    private final LocationMapper locationMapper;
    private final ExplorationEventMapper eventMapper;
    private final PlayerStoryLogMapper storyLogMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final RecoveryService recoveryService;
    private final PlayerLogService playerLogService;
    private final TitleService titleService;
    private final InventoryService inventoryService;
    private final EquipmentService equipmentService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(ExploreServiceImpl.class);

    public ExploreServiceImpl(PlayerMapper playerMapper,
                              LocationMapper locationMapper,
                              ExplorationEventMapper eventMapper,
                              PlayerStoryLogMapper storyLogMapper,
                              PlayerTitleMapper playerTitleMapper,
                              RecoveryService recoveryService,
                              PlayerLogService playerLogService,
                              TitleService titleService,
                              InventoryService inventoryService,
                              EquipmentService equipmentService,
                              BroadcastService broadcastService,
                              FactionService factionService,
                              QuestService questService,
                              ObjectMapper objectMapper,
                              DataSource dataSource) {
        this.playerMapper = playerMapper;
        this.locationMapper = locationMapper;
        this.eventMapper = eventMapper;
        this.storyLogMapper = storyLogMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.recoveryService = recoveryService;
        this.playerLogService = playerLogService;
        this.titleService = titleService;
        this.inventoryService = inventoryService;
        this.equipmentService = equipmentService;
        this.broadcastService = broadcastService;
        this.factionService = factionService;
        this.questService = questService;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void seedExplorationData() {
        try {
            Long count = eventMapper.selectCount(null);
            if (count != null && count >= 70) {
                log.info("Exploration events already seeded ({} events), skipping", count);
                return;
            }
            log.info("Seeding exploration data...");
            // 清除旧数据
            eventMapper.delete(new QueryWrapper<>());
            locationMapper.delete(new QueryWrapper<>());

            // 从 JSON 文件加载种子数据
            ClassPathResource resource = new ClassPathResource("db/seed_explore.json");
            List<Map<String, Object>> seedData = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> entry : seedData) {
                String type = (String) entry.get("type");
                if ("location".equals(type)) {
                    Location loc = new Location();
                    loc.setLocationKey((String) entry.get("location_key"));
                    loc.setName((String) entry.get("name"));
                    loc.setDescription((String) entry.get("description"));
                    loc.setUnlockConditionsJson(toJson(entry.get("unlock_conditions")));
                    loc.setEventRatesJson(toJson(entry.get("event_rates")));
                    loc.setMinLevel(entry.get("min_level") != null ? ((Number) entry.get("min_level")).intValue() : 1);
                    loc.setDangerLevel(entry.get("danger_level") != null ? ((Number) entry.get("danger_level")).intValue() : 1);
                    loc.setRecommendedRank((String) entry.get("recommended_rank"));
                    loc.setIsDefault(entry.get("is_default") != null ? ((Number) entry.get("is_default")).intValue() : 0);
                    try { locationMapper.insert(loc); } catch (Exception ignored) {}
                } else if ("event".equals(type)) {
                    ExplorationEvent ev = new ExplorationEvent();
                    ev.setEventKey((String) entry.get("event_key"));
                    ev.setEventType((String) entry.get("event_type"));
                    ev.setStageKey((String) entry.get("stage_key"));
                    ev.setLocationKey((String) entry.get("location_key"));
                    ev.setName((String) entry.get("name"));
                    ev.setDescription((String) entry.get("description"));
                    ev.setWeight(entry.get("weight") != null ? ((Number) entry.get("weight")).intValue() : 10);
                    ev.setStaminaCost(entry.get("stamina_cost") != null ? ((Number) entry.get("stamina_cost")).intValue() : 5);
                    ev.setRepeatable(entry.get("repeatable") != null ? ((Number) entry.get("repeatable")).intValue() : 1);
                    ev.setRewardsJson(toJson(entry.get("rewards")));
                    ev.setProgressEffectsJson(toJson(entry.get("progress_effects")));
                    ev.setRisksJson(toJson(entry.get("risks")));
                    ev.setChoicesJson(toJson(entry.get("choices")));
                    ev.setLogTemplate((String) entry.get("log_template"));
                    ev.setEnabled(1);
                    try { eventMapper.insert(ev); } catch (Exception ignored) {}
                }
            }
            log.info("Exploration seed complete: {} entries", seedData.size());
        } catch (Exception e) {
            log.warn("Exploration seed failed: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
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

        // 5a. 检查低等级地点是否有未完成的剧情事件
        List<String> lowerUnfinishedStories = findLowerUnfinishedStories(locationKey, playerId);

        // 6. 获取可用事件 (按 event_rates_json 过滤类型，排除已触发的非可重复故事)
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        int storyPity = toInt(stageProgress.get("storyPity"), 0);

        QueryWrapper<ExplorationEvent> evQuery = new QueryWrapper<>();
        evQuery.eq("location_key", locationKey)
               .eq("enabled", 1);
        List<ExplorationEvent> allEvents = eventMapper.selectList(evQuery);

        // 排除已触发且不可重复的事件
        @SuppressWarnings("unchecked")
        List<String> triggeredEvents = (List<String>) stageProgress.getOrDefault(
                "storyEventsTriggered", new ArrayList<>());
        List<ExplorationEvent> availableEvents = new ArrayList<>();
        for (ExplorationEvent e : allEvents) {
            if (triggeredEvents.contains(e.getEventKey()) && e.getRepeatable() != null && e.getRepeatable() == 0) {
                continue;
            }
            // 检查 required_conditions_json
            if (!checkRequiredConditions(stats, stageProgress, parseJsonMap(e.getRequiredConditionsJson()))) {
                continue;
            }
            // 低等级地点有未完成剧情 → 当前地点屏蔽 story/side_story 事件
            if (!lowerUnfinishedStories.isEmpty() &&
                ("story".equals(e.getEventType()) || "side_story".equals(e.getEventType()))) {
                continue;
            }
            availableEvents.add(e);
        }
        // 记录屏蔽原因，供前端展示
        final boolean storiesBlocked = !lowerUnfinishedStories.isEmpty();

        if (availableEvents.isEmpty())
            throw new BusinessException(ErrorCode.NO_AVAILABLE_EVENT, "该地点暂无可用的探索事件");

        // 7. 按 event_rates_json 权重 + storyPity 保底抽取事件
        ExplorationEvent selected = selectEvent(availableEvents, storyPity, parseJsonMap(location.getEventRatesJson()));

        // 7a. 检测该地点故事是否已全部耗尽
        boolean storiesExhausted = checkStoriesExhausted(locationKey, allEvents, triggeredEvents);

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
            battleResult.put("stories_exhausted", storiesExhausted);
            battleResult.put("stories_blocked", storiesBlocked);
            battleResult.put("lower_unfinished", lowerUnfinishedStories);
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
        // 包含分支选项 (仅 story 类型)
        if ("story".equals(selected.getEventType()) && selected.getChoicesJson() != null
                && !selected.getChoicesJson().isBlank()) {
            eventData.put("choices", parseJsonList(selected.getChoicesJson()));
        }
        result.put("result", eventData);

        result.put("rewards", appliedRewards);
        result.put("progress_effects", appliedProgress);
        result.put("new_titles", newTitles);
        result.put("stories_exhausted", storiesExhausted);
        result.put("stories_blocked", storiesBlocked);
        result.put("lower_unfinished", lowerUnfinishedStories);

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

    private ExplorationEvent selectEvent(List<ExplorationEvent> events, int storyPity,
                                         Map<String, Object> eventRates) {
        // 保底: storyPity >= 8 → 优先选 story 事件
        if (storyPity >= 8) {
            for (ExplorationEvent e : events) {
                if ("story".equals(e.getEventType())) {
                    return e;
                }
            }
        }

        // 按 event_rates_json 进行加权: base_weight × type_rate_bonus
        int totalWeight = 0;
        Map<String, Integer> adjustedWeights = new LinkedHashMap<>();
        for (ExplorationEvent e : events) {
            int w = e.getWeight();
            // 应用 event_rates 倍率 (event_rates 是百分比基准, 100 = 1x)
            if (eventRates.containsKey(e.getEventType())) {
                double rateFactor = ((Number) eventRates.get(e.getEventType())).doubleValue() / 100.0;
                w = Math.max(1, (int) Math.round(w * rateFactor));
            }
            // storyPity 加成
            if ("story".equals(e.getEventType())) {
                w = w + storyPity * (w / 2);
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

    // ─── 低等级地点未完成剧情检测 ───

    private List<String> findLowerUnfinishedStories(String currentLocationKey, Long playerId) {
        Location currentLocation = locationMapper.selectOne(
                new QueryWrapper<Location>().eq("location_key", currentLocationKey));
        if (currentLocation == null || currentLocation.getMinLevel() == null) {
            return Collections.emptyList();
        }
        int currentMinLevel = currentLocation.getMinLevel();

        QueryWrapper<Location> locQw = new QueryWrapper<>();
        locQw.lt("min_level", currentMinLevel);
        List<Location> lowerLocations = locationMapper.selectList(locQw);
        if (lowerLocations.isEmpty()) {
            return Collections.emptyList();
        }

        Player player = playerMapper.selectById(playerId);
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        @SuppressWarnings("unchecked")
        List<String> triggeredEvents = (List<String>) stageProgress.getOrDefault(
                "storyEventsTriggered", new ArrayList<>());

        List<String> result = new ArrayList<>();
        for (Location loc : lowerLocations) {
            QueryWrapper<ExplorationEvent> evQw = new QueryWrapper<>();
            evQw.eq("location_key", loc.getLocationKey())
               .eq("event_type", "story")
               .eq("enabled", 1);
            List<ExplorationEvent> storyEvents = eventMapper.selectList(evQw);

            long unfinishedCount = storyEvents.stream()
                    .filter(e -> !triggeredEvents.contains(e.getEventKey()))
                    .count();

            if (unfinishedCount > 0) {
                result.add(loc.getName() + "(" + unfinishedCount + ")");
            }
        }
        return result;
    }

    // ─── 故事耗尽检测 ───

    private boolean checkStoriesExhausted(String locationKey, List<ExplorationEvent> allEvents,
                                          List<String> triggeredEvents) {
        for (ExplorationEvent e : allEvents) {
            if (!"story".equals(e.getEventType())) continue;
            if (!triggeredEvents.contains(e.getEventKey()) && e.getEnabled() != null && e.getEnabled() == 1) {
                return false; // 还有未触发的故事事件
            }
        }
        return true;
    }

    // ─── 条件检查 ───

    @SuppressWarnings("unchecked")
    private boolean checkRequiredConditions(Map<String, Object> stats,
                                            Map<String, Object> stageProgress,
                                            Map<String, Object> conditions) {
        if (conditions.isEmpty()) return true;
        // required_level
        if (conditions.containsKey("required_level")) {
            int required = toInt(conditions.get("required_level"), 0);
            if (toInt(stats.get("level"), 1) < required) return false;
        }
        // required_events: 需要先触发某些事件
        if (conditions.containsKey("required_events")) {
            List<String> requiredEvents = (List<String>) conditions.get("required_events");
            List<String> triggered = (List<String>) stageProgress.getOrDefault(
                    "storyEventsTriggered", Collections.emptyList());
            for (String re : requiredEvents) {
                if (!triggered.contains(re)) return false;
            }
        }
        // required_rank
        if (conditions.containsKey("required_rank")) {
            String required = (String) conditions.get("required_rank");
            String rank = (String) stats.getOrDefault("avatarRank", "F");
            if (rank.compareTo(required) < 0) return false;
        }
        return true;
    }

    // ─── 选项处理 ───

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> makeChoice(Long playerId, String eventKey, int choiceIndex, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        // 获取事件
        QueryWrapper<ExplorationEvent> qw = new QueryWrapper<>();
        qw.eq("event_key", eventKey);
        ExplorationEvent event = eventMapper.selectOne(qw);
        if (event == null)
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "事件不存在");
        if (event.getChoicesJson() == null || event.getChoicesJson().isBlank())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该事件没有选项");

        List<Map<String, Object>> choices = (List<Map<String, Object>>) parseJsonList(event.getChoicesJson());
        if (choiceIndex < 0 || choiceIndex >= choices.size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的选项");

        // 检查玩家是否已经触发过此事件 (防止重复选择)
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        List<String> triggered = (List<String>) stageProgress.getOrDefault(
                "storyEventsTriggered", new ArrayList<>());
        if (!triggered.contains(eventKey))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先触发此事件再进行选择");

        Map<String, Object> chosen = choices.get(choiceIndex);

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event_key", eventKey);
        result.put("event_name", event.getName());
        result.put("choice_index", choiceIndex);
        result.put("choice_label", chosen.get("label"));
        result.put("consequence_text", chosen.getOrDefault("consequence_text", ""));

        // 应用选项覆盖奖励
        Map<String, Object> rewardsOverride = null;
        if (chosen.containsKey("rewards_override") && chosen.get("rewards_override") instanceof Map) {
            rewardsOverride = (Map<String, Object>) chosen.get("rewards_override");
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            Map<String, Object> applied = applyChoiceRewards(player, stats, rewardsOverride);
            result.put("rewards", applied);
            try { player.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
        } else {
            result.put("rewards", new LinkedHashMap<>());
        }

        // 解锁地点
        List<String> unlockLocs = new ArrayList<>();
        if (chosen.containsKey("unlock_locations") && chosen.get("unlock_locations") instanceof List) {
            unlockLocs = (List<String>) chosen.get("unlock_locations");
            result.put("unlock_locations", unlockLocs);
        } else {
            result.put("unlock_locations", new ArrayList<>());
        }
        // 解锁地点中文名
        List<String> unlockLocNames = new ArrayList<>();
        for (String locKey : unlockLocs) {
            QueryWrapper<Location> lq2 = new QueryWrapper<>();
            lq2.eq("location_key", locKey);
            Location loc2 = locationMapper.selectOne(lq2);
            unlockLocNames.add(loc2 != null && loc2.getName() != null ? loc2.getName() : locKey);
        }
        result.put("unlock_location_names", unlockLocNames);

        // 解锁事件
        List<String> unlockEvents = new ArrayList<>();
        if (chosen.containsKey("unlock_events") && chosen.get("unlock_events") instanceof List) {
            unlockEvents = (List<String>) chosen.get("unlock_events");
            result.put("unlock_events", unlockEvents);
        } else {
            result.put("unlock_events", new ArrayList<>());
        }

        // 称号偏向
        if (chosen.containsKey("title_bias") && chosen.get("title_bias") instanceof Map) {
            Map<String, Object> titleBias = (Map<String, Object>) chosen.get("title_bias");
            result.put("title_bias", titleBias);
            // 添加到 stage_progress 中供 titleService 评估
            Map<String, Object> tbProgress = (Map<String, Object>) stageProgress.computeIfAbsent(
                    "titleBiasProgress", k -> new LinkedHashMap<>());
            for (Map.Entry<String, Object> entry : titleBias.entrySet()) {
                int add = entry.getValue() instanceof Number ? ((Number) entry.getValue()).intValue() : 0;
                int cur = toInt(tbProgress.get(entry.getKey()), 0);
                tbProgress.put(entry.getKey(), cur + add);
            }
        }

        // ── P0: 持久化解锁地点 ──
        Map<String, Object> playerFlags = parseJsonMap(player.getStoryFlagsJson());
        @SuppressWarnings("unchecked")
        List<String> unlockedLocations = (List<String>) playerFlags.computeIfAbsent(
                "unlocked_locations", k -> new ArrayList<>());
        for (String locKey : unlockLocs) {
            if (!unlockedLocations.contains(locKey)) {
                unlockedLocations.add(locKey);
            }
        }
        playerFlags.put("unlocked_locations", unlockedLocations);

        // ── P0: 记录决策历史 ──
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisionHistory = (List<Map<String, Object>>) parseJsonList(
                player.getDecisionHistoryJson());
        if (decisionHistory == null) decisionHistory = new ArrayList<>();
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("event_key", eventKey);
        decision.put("event_name", event.getName());
        decision.put("choice_index", choiceIndex);
        decision.put("choice_label", chosen.get("label"));
        decision.put("timestamp", LocalDateTime.now().toString());
        decisionHistory.add(decision);
        player.setDecisionHistoryJson(toJson(decisionHistory));

        // ── P0: 记录路线历史 (防重复领奖) ──
        @SuppressWarnings("unchecked")
        List<String> routeHistory = (List<String>) stageProgress.computeIfAbsent(
                "routeHistory", k -> new ArrayList<>());
        if (!routeHistory.contains(eventKey)) {
            routeHistory.add(eventKey);
        }

        // 写入玩家故事日志
        PlayerStoryLog log = new PlayerStoryLog();
        log.setPlayerId(playerId);
        log.setEventKey(eventKey);
        log.setLocationKey(event.getLocationKey() != null ? event.getLocationKey() : "");
        log.setLocationName("");
        log.setEventName(event.getName());
        log.setChoiceIndex(choiceIndex);
        log.setChoiceLabel((String) chosen.get("label"));
        log.setConsequenceText((String) chosen.getOrDefault("consequence_text", ""));
        try {
            log.setRewardsSnapshot(rewardsOverride != null ? objectMapper.writeValueAsString(rewardsOverride) : "{}");
        } catch (Exception ignored) {}
        storyLogMapper.insert(log);

        // ── P0: 章节/阶段推进 ──
        advanceChapter(player, stageProgress, event);

        // 日志
        playerLogService.addLog(playerId, "story", "你选择了: " + chosen.get("label"));

        // 保存玩家
        try {
            player.setStageProgressJson(objectMapper.writeValueAsString(stageProgress));
            player.setStoryFlagsJson(objectMapper.writeValueAsString(playerFlags));
        } catch (Exception ignored) {}
        playerMapper.updateById(player);

        // 评估称号
        List<PlayerTitleVO> newTitles = titleService.evaluateAndUnlockTitles(playerId, null);
        result.put("new_titles", newTitles);

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyChoiceRewards(Player player, Map<String, Object> stats,
                                                   Map<String, Object> rewards) {
        Map<String, Object> applied = new LinkedHashMap<>();
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
        // channelHeat
        if (rewards.containsKey("channelHeat")) {
            int add = toInt(rewards.get("channelHeat"), 0);
            int cur = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", cur + add);
            applied.put("channelHeat", add);
        }
        // exp
        if (rewards.containsKey("exp")) {
            int add = toInt(rewards.get("exp"), 0);
            double mult = expMultiplier((String) stats.getOrDefault("avatarRank", "F"));
            int adjusted = Math.max(1, (int) Math.round(add * mult));
            int totalExp = toInt(stats.get("exp"), 0) + adjusted;
            stats.put("exp", totalExp);
            applied.put("exp", adjusted);
            int newLevel = (int) Math.floor(Math.sqrt(totalExp / 100.0)) + 1;
            int oldLevel = toInt(stats.get("level"), 1);
            if (newLevel > oldLevel) {
                stats.put("level", newLevel);
                stats.put("hp", toInt(stats.get("maxHp"), 100));
                stats.put("freePoints", toInt(stats.get("freePoints"), 0) + 3 * (newLevel - oldLevel));
            }
        }
        // items
        if (rewards.containsKey("items") && rewards.get("items") instanceof List) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) rewards.get("items");
            List<Map<String, Object>> acquired = new ArrayList<>();
            for (Map<String, Object> drop : items) {
                String itemKey = (String) drop.get("itemKey");
                if (itemKey == null) continue;
                int qty = toInt(drop.get("quantity"), 1);
                int newQty = inventoryService.addItem(player.getId(), itemKey, qty);
                acquired.add(Map.of("itemKey", itemKey, "quantity", qty, "totalQuantity", newQty));
            }
            if (!acquired.isEmpty()) applied.put("items", acquired);
        }
        // equipment
        if (rewards.containsKey("equipment") && rewards.get("equipment") instanceof List) {
            List<Map<String, Object>> equips = (List<Map<String, Object>>) rewards.get("equipment");
            List<Map<String, Object>> acquiredEquip = new ArrayList<>();
            for (Map<String, Object> drop : equips) {
                String equipKey = (String) drop.get("equipmentKey");
                if (equipKey == null) continue;
                int r = equipmentService.addEquipment(player.getId(), equipKey, "story_choice");
                if (r > 0) acquiredEquip.add(Map.of("equipmentKey", equipKey));
            }
            if (!acquiredEquip.isEmpty()) applied.put("equipment", acquiredEquip);
        }
        return applied;
    }

    // ─── P0: 章节推进 ───

    @SuppressWarnings("unchecked")
    private void advanceChapter(Player player, Map<String, Object> stageProgress, ExplorationEvent event) {
        // 从事件的 stage_key 或 progress_effects 推断章节信息
        String stageKey = event.getStageKey();
        if (stageKey == null || stageKey.isBlank()) return;

        // 更新 current_stage
        player.setCurrentStage(stageKey);

        // 统计该章节已完成的 story 事件数
        Map<String, Integer> chapterEventCounts = (Map<String, Integer>) stageProgress.get("chapterEventCounts");
        if (chapterEventCounts == null) {
            chapterEventCounts = new LinkedHashMap<>();
            stageProgress.put("chapterEventCounts", chapterEventCounts);
        }
        String chapterKey = event.getLocationKey() + "_ch" + extractChapterNum(stageKey);
        int count = chapterEventCounts.getOrDefault(chapterKey, 0) + 1;
        chapterEventCounts.put(chapterKey, count);

        // 更新 current_chapter (简单策略: 基于 stageKey 前缀)
        player.setCurrentChapter(chapterKey);
    }

    private int extractChapterNum(String stageKey) {
        // 从 "ch01", "ch1", "chapter_1" 等格式提取数字
        String num = stageKey.replaceAll("[^0-9]", "");
        try { return Integer.parseInt(num); } catch (NumberFormatException e) { return 1; }
    }

    // ─── 故事回顾 ───

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStoryLog(Long playerId) {
        QueryWrapper<PlayerStoryLog> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).orderByAsc("created_at");
        List<PlayerStoryLog> logs = storyLogMapper.selectList(qw);

        // 按地点分组
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (PlayerStoryLog log : logs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("event_key", log.getEventKey());
            entry.put("event_name", log.getEventName());
            entry.put("location_key", log.getLocationKey());
            entry.put("choice_index", log.getChoiceIndex());
            entry.put("choice_label", log.getChoiceLabel());
            entry.put("consequence_text", log.getConsequenceText());
            entry.put("rewards", parseJsonList(log.getRewardsSnapshot()));
            entry.put("created_at", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
            String locKey = log.getLocationKey() != null ? log.getLocationKey() : "unknown";
            grouped.computeIfAbsent(locKey, k -> new ArrayList<>()).add(entry);
        }

        // 转为时间线列表 (按地点分组)
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("location_key", entry.getKey());
            // 查地点名称
            QueryWrapper<Location> lq = new QueryWrapper<>();
            lq.eq("location_key", entry.getKey());
            Location loc = locationMapper.selectOne(lq);
            group.put("location_name", loc != null ? loc.getName() : entry.getKey());
            group.put("stories", entry.getValue());
            result.add(group);
        }
        return result;
    }

    // ─── 新增工具方法 ───

    private Object parseJsonList(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e2) {
                return new ArrayList<>();
            }
        }
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

        // exp (位阶倍率加成: 位阶越高经验越多)
        if (rewards.containsKey("exp")) {
            int add = toInt(rewards.get("exp"), 0);
            double multiplier = expMultiplier((String) stats.getOrDefault("avatarRank", "F"));
            int adjustedAdd = Math.max(1, (int) Math.round(add * multiplier));
            int current = toInt(stats.get("exp"), 0);
            int totalExp = current + adjustedAdd;
            stats.put("exp", totalExp);
            applied.put("exp", adjustedAdd);
            // 升级检查: level = floor(sqrt(totalExp / 100)) + 1
            int newLevel = (int) Math.floor(Math.sqrt(totalExp / 100.0)) + 1;
            int oldLevel = toInt(stats.get("level"), 1);
            if (newLevel > oldLevel) {
                int levelsGained = newLevel - oldLevel;
                stats.put("level", newLevel);
                stats.put("hp", toInt(stats.get("maxHp"), 100));
                stats.put("freePoints", toInt(stats.get("freePoints"), 0) + 3 * levelsGained);
            }
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

    /** 位阶经验倍率: 阶梯型增长，越高位阶经验加成越大 */
    private double expMultiplier(String rankKey) {
        if (rankKey == null) return 1.0;
        return switch (rankKey.toUpperCase()) {
            case "E" -> 1.5;
            case "D" -> 2.5;
            case "C" -> 4.0;
            case "B" -> 7.0;
            case "A" -> 12.0;
            case "S" -> 20.0;
            case "SS" -> 35.0;
            case "SSS" -> 60.0;
            default -> 1.0;
        };
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
            case "story":                return "story";
            case "resource":             return "exploration";
            case "opportunity":          return "exploration";
            case "boss_clue":            return "milestone";
            case "empty":                return "info";
            case "battle_placeholder":   return "battle";
            default:                     return "exploration";
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
        String locName = player.getCurrentLocation();
        if (locName != null && !locName.isEmpty()) {
            QueryWrapper<Location> lq = new QueryWrapper<>();
            lq.eq("location_key", locName);
            Location loc = locationMapper.selectOne(lq);
            if (loc != null && loc.getName() != null && !loc.getName().isEmpty()) {
                locName = loc.getName();
            }
        }
        snapshot.put("current_location_name", locName);
        snapshot.put("stats", new LinkedHashMap<>(stats));
        snapshot.put("stage_progress", new LinkedHashMap<>(stageProgress));
        return snapshot;
    }
}
