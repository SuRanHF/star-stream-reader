package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
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
import com.huazhenghai.readergame.service.ExperienceProgression;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.LocationUnlockPolicy;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.SkillService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.EquipmentVO;
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
    private final PlayerResourceTransactionService resourceTransactionService;
    private final TitleService titleService;
    private final InventoryService inventoryService;
    private final EquipmentService equipmentService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final SkillService skillService;
    private final PlayerEffectService playerEffectService;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private static final String SKILL_STAMINA_SAVINGS_REMAINDER = "skillStaminaSavingsRemainder";
    private static final Map<String, Integer> AVATAR_RANK_ORDER = Map.of(
            "F", 1, "E", 2, "D", 3, "C", 4, "B", 5,
            "A", 6, "S", 7, "SS", 8, "SSS", 9);

    private static final Logger log = LoggerFactory.getLogger(ExploreServiceImpl.class);

    public ExploreServiceImpl(PlayerMapper playerMapper,
                              LocationMapper locationMapper,
                              ExplorationEventMapper eventMapper,
                              PlayerStoryLogMapper storyLogMapper,
                              PlayerTitleMapper playerTitleMapper,
                              RecoveryService recoveryService,
                              PlayerLogService playerLogService,
                              PlayerResourceTransactionService resourceTransactionService,
                              TitleService titleService,
                              InventoryService inventoryService,
                              EquipmentService equipmentService,
                              BroadcastService broadcastService,
                              FactionService factionService,
                              QuestService questService,
                              SkillService skillService,
                              ObjectMapper objectMapper,
                              DataSource dataSource,
                              PlayerEffectService playerEffectService) {
        this.playerMapper = playerMapper;
        this.locationMapper = locationMapper;
        this.eventMapper = eventMapper;
        this.storyLogMapper = storyLogMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.recoveryService = recoveryService;
        this.playerLogService = playerLogService;
        this.resourceTransactionService = resourceTransactionService;
        this.titleService = titleService;
        this.inventoryService = inventoryService;
        this.equipmentService = equipmentService;
        this.broadcastService = broadcastService;
        this.factionService = factionService;
        this.questService = questService;
        this.skillService = skillService;
        this.playerEffectService = playerEffectService;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void seedExplorationData() {
        try {
            Long count = eventMapper.selectCount(null);
            log.info("Ensuring exploration seed data exists ({} existing events); existing configuration is preserved",
                    count != null ? count : 0);

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
                    ev.setRequiredConditionsJson(toJson(entry.get("required_conditions")));
                    ev.setRewardsJson(toJson(entry.get("rewards")));
                    ev.setProgressEffectsJson(toJson(entry.get("progress_effects")));
                    Object risks = entry.containsKey("risks") ? entry.get("risks") : entry.get("risks_json");
                    ev.setRisksJson(toJson(risks));
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
    public Map<String, Object> startExplore(Long playerId, String locationKey,
                                           String requestKey, Long userId) {
        if (requestKey == null || requestKey.isBlank())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 不能为空");
        if (requestKey.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 长度不能超过 64");

        // 1. 校验玩家
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        // 2. 先检查回执，再恢复体力或抽取事件；重放不能消耗资源或触发新事件。
        if (locationKey == null || locationKey.isBlank()) {
            locationKey = player.getCurrentLocation();
        }
        if (locationKey == null || locationKey.isBlank()) {
            locationKey = "ruined_station";
        }
        Map<String, Object> receipt = playerLogService.findExplorationReceipt(playerId, requestKey);
        if (receipt != null) return replayExploreReceipt(player, locationKey, requestKey, receipt);

        // Preserve idempotent replay above, but do not let a fresh action bypass a battle
        // encounter that the UI requires the player to resolve first.
        if (!playerLogService.findPendingBattleEncounters(playerId).isEmpty()) {
            throw new BusinessException(ErrorCode.PENDING_BATTLE_UNRESOLVED,
                    "请先处理当前待战斗遭遇，再继续探索");
        }

        // 3. 应用恢复
        Map<String, Object> stats = recoveryService.applyRecovery(player);
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        List<String> triggeredStoryEvents = toStringList(stageProgress.get("storyEventsTriggered"));
        Object decisionHistoryValue = parseJsonList(player.getDecisionHistoryJson());
        List<?> decisionHistory = decisionHistoryValue instanceof List<?> history
                ? history : Collections.emptyList();
        String statsBeforeExplore = player.getStatsJson();
        String progressBeforeExplore = player.getStageProgressJson();
        String locationBeforeExplore = player.getCurrentLocation();
        Integer coinsBeforeExplore = player.getCoins();
        Integer fragmentsBeforeExplore = player.getStoryFragments();

        // 4. 检查休息状态
        recoveryService.assertCanAct(player);

        // 5. 确定并校验地点
        QueryWrapper<Location> locQuery = new QueryWrapper<>();
        locQuery.eq("location_key", locationKey);
        Location location = locationMapper.selectOne(locQuery);
        if (location == null)
            throw new BusinessException(ErrorCode.LOCATION_NOT_FOUND, "该地点不存在");

        // 5. 检查解锁条件
        Map<String, Object> unlockConditions = parseJsonMap(location.getUnlockConditionsJson());
        if (!LocationUnlockPolicy.canAccessLocation(
                unlockConditions,
                stats,
                parseJsonMap(player.getStoryFlagsJson()),
                player.getCurrentChapter(),
                triggeredStoryEvents))
            throw new BusinessException(ErrorCode.LOCATION_LOCKED, "该地点尚未解锁");

        // 5a. 检查低等级地点是否有未完成的剧情事件
        List<String> lowerUnfinishedStories = findLowerUnfinishedStories(
                location, stageProgress, decisionHistory);

        // 6. 获取可用事件 (按 event_rates_json 过滤类型，排除已触发的非可重复故事)
        int storyPity = toInt(stageProgress.get("storyPity"), 0);

        QueryWrapper<ExplorationEvent> evQuery = new QueryWrapper<>();
        evQuery.eq("location_key", locationKey)
               .eq("enabled", 1);
        List<ExplorationEvent> allEvents = eventMapper.selectList(evQuery);

        // 排除已触发且不可重复的事件
        List<String> triggeredSideEvents = toStringList(stageProgress.get("sideEventsTriggered"));
        Object pendingSnapshotsValue = stageProgress.get("pendingStorySnapshots");
        Map<?, ?> pendingStorySnapshotMap = pendingSnapshotsValue instanceof Map<?, ?> snapshots
                ? snapshots : Collections.emptyMap();
        List<ExplorationEvent> availableEvents = new ArrayList<>();
        boolean pendingStoryWasSkipped = false;
        for (ExplorationEvent e : allEvents) {
            List<String> triggeredEvents = "story".equals(e.getEventType())
                    ? triggeredStoryEvents : triggeredSideEvents;
            if (triggeredEvents.contains(e.getEventKey())
                    && e.getRepeatable() != null && e.getRepeatable() == 0) {
                continue;
            }
            // Snapshots are keyed by eventKey; do not overwrite an unresolved repeatable story instance.
            if ("story".equals(e.getEventType())
                    && pendingStorySnapshotMap.containsKey(e.getEventKey())) {
                Object parsedChoices = parseJsonList(e.getChoicesJson());
                if (parsedChoices instanceof List<?> choices && !choices.isEmpty()) {
                    pendingStoryWasSkipped = true;
                    continue;
                }
            }
            // 检查 required_conditions_json
            if (!checkRequiredConditions(
                    stats, stageProgress, decisionHistory, parseJsonMap(e.getRequiredConditionsJson()))) {
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

        if (availableEvents.isEmpty() && pendingStoryWasSkipped)
            throw new BusinessException(ErrorCode.NO_AVAILABLE_EVENT,
                    "还有未完成的剧情选择，请先继续剧情再探索");
        if (availableEvents.isEmpty())
            throw new BusinessException(ErrorCode.NO_AVAILABLE_EVENT, "该地点暂无可用的探索事件");

        Map<String, Object> skillBonuses = skillService.calculateSkillBonus(playerId);
        if (skillBonuses == null) skillBonuses = Collections.emptyMap();
        Map<String, Object> titleEffects = titleService.calculateTitleEffects(playerId);
        if (titleEffects == null) titleEffects = Collections.emptyMap();
        Map<String, Object> equipmentEffects = equipmentService.calculateEquipmentSpecialEffectBonuses(playerId);
        if (equipmentEffects == null) equipmentEffects = Collections.emptyMap();
        double storyEventBonus = combineEffectBonus(
                skillBonuses, titleEffects, equipmentEffects, "storyEventBonus");

        // 7. 按 event_rates_json 权重 + storyPity 保底抽取事件
        ExplorationEvent selected = selectEvent(availableEvents, storyPity,
                parseJsonMap(location.getEventRatesJson()), storyEventBonus);

        // 7a. 检测该地点故事是否已全部耗尽
        boolean storiesExhausted = checkStoriesExhausted(
                allEvents, triggeredStoryEvents, decisionHistory);

        // 每次探索抽取结果时扣除一次行动体力，包括战斗遭遇；迎战、撤退和同请求重放都不再额外扣除。
        double staminaCostReduce = skillBonuses.get("staminaCostReduce") instanceof Number value
                ? value.doubleValue() : 0.0;
        int baseStaminaCost = selected.getStaminaCost() != null ? selected.getStaminaCost() : 5;
        int staminaCost = applySkillStaminaCostReduction(
                baseStaminaCost, staminaCostReduce, stageProgress);
        int stamina = toInt(stats.get("stamina"), 50);
        if (stamina < staminaCost)
            throw new BusinessException(ErrorCode.STAMINA_NOT_ENOUGH,
                    "体力不足，需要" + staminaCost + "点体力");
        stats.put("stamina", stamina - staminaCost);

        // battle_placeholder 只返回遭遇；战斗奖励仍由 CombatService 结算。
        if ("battle_placeholder".equals(selected.getEventType())) {
            player.setCurrentLocation(locationKey);
            recordActionResourceChanges(player, "explore", selected.getEventKey(), requestKey,
                    toInt(coinsBeforeExplore, 0), toInt(fragmentsBeforeExplore, 0),
                    parseJsonMap(statsBeforeExplore),
                    stats, Collections.emptyMap());
            persistExplorePlayerState(player, stats, stageProgress,
                    statsBeforeExplore, progressBeforeExplore, locationBeforeExplore,
                    coinsBeforeExplore, fragmentsBeforeExplore);

            Map<String, Object> battleResult = new LinkedHashMap<>();
            battleResult.put("result_type", "battle");
            battleResult.put("stamina_cost_paid", staminaCost);
            battleResult.put("location_key", location.getLocationKey());
            battleResult.put("location_name", location.getName());
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
            battleResult.put("request_key", requestKey);
            battleResult.put("request_replayed", false);
            Map<String, Object> receiptResponse = new LinkedHashMap<>(battleResult);
            receiptResponse.remove("player");
            Map<String, Object> receiptMetadata = new LinkedHashMap<>();
            receiptMetadata.put("kind", "exploration_result");
            receiptMetadata.put("eventKey", selected.getEventKey());
            receiptMetadata.put("eventType", selected.getEventType());
            receiptMetadata.put("requestKey", requestKey);
            receiptMetadata.put("requestLocationKey", locationKey);
            receiptMetadata.put("staminaCost", staminaCost);
            receiptMetadata.put("requestResponse", receiptResponse);
            ensureReceiptSerializable(receiptMetadata);
            recordExploreCompletion(playerId, selected.getEventKey());
            playerLogService.addLog(playerId, "battle",
                    selected.getName() + "（遭遇，消耗体力" + staminaCost + "）", receiptMetadata);
            return battleResult;
        }
        // 更新当前地点
        player.setCurrentLocation(locationKey);

        // 10. 应用奖励
        Map<String, Object> appliedRewards = applyRewards(player, stats, selected.getRewardsJson());
        recordActionResourceChanges(player, "explore", selected.getEventKey(), requestKey,
                toInt(coinsBeforeExplore, 0), toInt(fragmentsBeforeExplore, 0),
                parseJsonMap(statsBeforeExplore),
                stats, appliedRewards);
        Object parsedChoices = parseJsonList(selected.getChoicesJson());
        boolean hasStoryChoices = "story".equals(selected.getEventType())
                && parsedChoices instanceof List<?> choices && !choices.isEmpty();

        // 11. 应用进度效果
        Map<String, Object> appliedProgress = applyProgressEffects(stageProgress, selected);
        StoryCombatRelationRules.unlockHintForEvent(selected.getEventKey())
                .ifPresent(hint -> appliedProgress.put("storyRelationHint", hint));

        Map<String, Object> pendingStorySnapshot = null;
        if (hasStoryChoices) {
            Map<String, Object> pendingSnapshots = (Map<String, Object>) stageProgress.computeIfAbsent(
                    "pendingStorySnapshots", k -> new LinkedHashMap<>());
            pendingStorySnapshot = new LinkedHashMap<>();
            pendingStorySnapshot.put("instanceKey", UUID.randomUUID().toString());
            pendingStorySnapshot.put("rewards", appliedRewards);
            pendingStorySnapshot.put("new_titles", new ArrayList<>());
            // Capture player-specific story echoes with this pending choice so later NPC progress
            // cannot change an already-issued scene or leak context between players.
            pendingStorySnapshot.put("personal_contexts", resolvePersonalContexts(selected, player));
            pendingSnapshots.put(selected.getEventKey(), pendingStorySnapshot);
        }

        // 12. 准备探索日志摘要；等称号结算完成后再持久化完整结果。
        String logMsg = selected.getLogTemplate();
        if (logMsg == null || logMsg.isBlank()) {
            logMsg = selected.getName();
        }
        logMsg += "（消耗体力" + staminaCost + "）";
        Object storyRelationHint = appliedProgress.get("storyRelationHint");
        if (storyRelationHint instanceof String hint && !hint.isBlank()) {
            logMsg += " " + hint;
        }
        String logType = mapEventTypeToLogType(selected.getEventType());

        // 13. 保存玩家状态
        persistExplorePlayerState(player, stats, stageProgress,
                statsBeforeExplore, progressBeforeExplore, locationBeforeExplore,
                coinsBeforeExplore, fragmentsBeforeExplore);

        // 14. 评估称号解锁
        List<PlayerTitleVO> newTitles = titleService.evaluateAndUnlockTitles(playerId, null);
        if (pendingStorySnapshot != null) {
            pendingStorySnapshot.put("new_titles", newTitles);
            String updatedStageProgress = serializePlayerState(stageProgress);
            // 称号评估依赖前面已保存的玩家进度，因此把实际解锁称号追加进待选快照后再保存一次。
            if (!Objects.equals(player.getStageProgressJson(), updatedStageProgress)) {
                player.setStageProgressJson(updatedStageProgress);
                if (playerMapper.updateById(player) <= 0) {
                    throw new IllegalStateException("剧情称号快照保存失败");
                }
            }
        }

        Map<String, Object> exploreLogMetadata = new LinkedHashMap<>();
        exploreLogMetadata.put("kind", "exploration_result");
        exploreLogMetadata.put("eventKey", selected.getEventKey());
        exploreLogMetadata.put("eventType", selected.getEventType());
        exploreLogMetadata.put("rewards", appliedRewards);
        exploreLogMetadata.put("newTitles", newTitles);
        exploreLogMetadata.put("staminaCost", staminaCost);

        // 14a. 自动贡献广播进度 (不阻断主流程)
        try {
            int contribValue = ("story".equals(selected.getEventType())
                    || "boss_clue".equals(selected.getEventType())) ? 2 : 1;
            String contribType = "story".equals(selected.getEventType()) ? "story" : "exploration";
            broadcastService.contribute("broadcast_station_cleanup", playerId, contribValue, contribType);
        } catch (Exception e) {
            log.warn("Broadcast contribution failed after exploration for player {}", playerId, e);
        }
        try {
            broadcastService.contributeToActiveAiEvents(playerId, "explore_location", 1);
            if ("story".equals(selected.getEventType())) {
                broadcastService.contributeToActiveAiEvents(playerId, "trigger_story", 1);
            }
        } catch (Exception e) {
            log.warn("AI broadcast contribution failed after exploration for player {}", playerId, e);
        }

        // 15. 构建返回结果 (snake_case keys 匹配前端)
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", selected.getEventType());
        result.put("stamina_cost_paid", staminaCost);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("event_key", selected.getEventKey());
        eventData.put("event_type", selected.getEventType());
        eventData.put("name", selected.getName());
        eventData.put("description", selected.getDescription());
        List<String> personalContexts = pendingStorySnapshot == null
                ? resolvePersonalContexts(selected, player)
                : toStringList(pendingStorySnapshot.get("personal_contexts"));
        if (!personalContexts.isEmpty()) eventData.put("personal_contexts", personalContexts);
        // 包含分支选项 (仅 story 类型)
        if ("story".equals(selected.getEventType()) && selected.getChoicesJson() != null
                && !selected.getChoicesJson().isBlank()) {
            eventData.put("choices", parseJsonList(selected.getChoicesJson()));
        }
        result.put("result", eventData);

        result.put("rewards", appliedRewards);
        result.put("progress_effects", appliedProgress);
        result.put("new_titles", newTitles);
        if (pendingStorySnapshot != null) {
            result.put("story_instance_key", pendingStorySnapshot.get("instanceKey"));
        }
        result.put("stories_exhausted", storiesExhausted);
        result.put("stories_blocked", storiesBlocked);
        result.put("lower_unfinished", lowerUnfinishedStories);

        // 玩家快照
        Map<String, Object> playerSnapshot = buildPlayerSnapshot(player, stats, stageProgress);
        result.put("player", playerSnapshot);

        result.put("request_key", requestKey);
        result.put("request_replayed", false);

        // new_logs
        List<Map<String, Object>> newLogs = new ArrayList<>();
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("message", logMsg);
        logEntry.put("type", logType);
        newLogs.add(logEntry);
        result.put("new_logs", newLogs);

        recordExploreCompletion(playerId, selected.getEventKey());

        exploreLogMetadata.put("requestKey", requestKey);
        exploreLogMetadata.put("requestLocationKey", locationKey);
        Map<String, Object> receiptResponse = new LinkedHashMap<>(result);
        receiptResponse.remove("player");
        exploreLogMetadata.put("requestResponse", receiptResponse);
        ensureReceiptSerializable(exploreLogMetadata);
        playerLogService.addLog(playerId, logType, logMsg, exploreLogMetadata);

        return result;
    }

    private void recordExploreCompletion(Long playerId, String eventKey) {
        try {
            factionService.contribute(playerId, null, "explore", 1L, "explore", eventKey, null);
        } catch (Exception e) {
            log.warn("Faction contribution failed after exploration for player {}", playerId, e);
        }
        questService.addProgress(playerId, "explore_count", 1, "explore", eventKey);
    }

    private void ensureReceiptSerializable(Map<String, Object> receiptMetadata) {
        try {
            objectMapper.writeValueAsString(receiptMetadata);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "探索回执无法保存，已取消本次结算");
        }
    }

    private void persistExplorePlayerState(Player player, Map<String, Object> stats,
                                           Map<String, Object> stageProgress,
                                           String priorStatsJson, String priorStageProgressJson,
                                           String priorLocation, Integer priorCoins,
                                           Integer priorStoryFragments) {
        String updatedStatsJson = serializePlayerState(stats);
        String updatedStageProgressJson = serializePlayerState(stageProgress);
        boolean changed = !Objects.equals(priorStatsJson, updatedStatsJson)
                || !Objects.equals(priorStageProgressJson, updatedStageProgressJson)
                || !Objects.equals(priorLocation, player.getCurrentLocation())
                || !Objects.equals(priorCoins, player.getCoins())
                || !Objects.equals(priorStoryFragments, player.getStoryFragments());
        player.setStatsJson(updatedStatsJson);
        player.setStageProgressJson(updatedStageProgressJson);
        if (changed && playerMapper.updateById(player) <= 0) {
            throw new IllegalStateException("探索结算失败，玩家进度未保存");
        }
    }

    private String serializePlayerState(Object state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception exception) {
            throw new IllegalStateException("探索状态序列化失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replayExploreReceipt(Player player, String locationKey,
                                                     String requestKey, Map<String, Object> receipt) {
        if (!Objects.equals(receipt.get("requestLocationKey"), locationKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该请求键已用于其他探索地点，请为本次探索重新生成请求键");
        }
        Object storedResponse = receipt.get("requestResponse");
        if (!(storedResponse instanceof Map<?, ?> response)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "探索回执缺失，已阻止重复结算");
        }

        Map<String, Object> replay = new LinkedHashMap<>((Map<String, Object>) response);
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        replay.put("player", buildPlayerSnapshot(player, stats, stageProgress));
        replay.put("request_key", requestKey);
        replay.put("request_replayed", true);
        return replay;
    }

    static int applySkillStaminaCostReduction(int baseCost, double reduction,
                                               Map<String, Object> stageProgress) {
        if (baseCost <= 0) return 0;

        double safeReduction = Double.isFinite(reduction)
                ? Math.min(0.9, Math.max(0.0, reduction)) : 0.0;
        Object savedRemainder = stageProgress.get(SKILL_STAMINA_SAVINGS_REMAINDER);
        double remainder = savedRemainder instanceof Number value ? value.doubleValue() : 0.0;
        if (!Double.isFinite(remainder) || remainder < 0.0 || remainder >= 1.0) remainder = 0.0;

        double accruedSavings = remainder + baseCost * safeReduction;
        int wholePointsSaved = (int) Math.floor(accruedSavings + 1.0e-9);
        double nextRemainder = accruedSavings - wholePointsSaved;
        int paidCost = Math.max(1, baseCost - wholePointsSaved);

        if (nextRemainder > 1.0e-9) {
            stageProgress.put(SKILL_STAMINA_SAVINGS_REMAINDER, nextRemainder);
        } else {
            stageProgress.remove(SKILL_STAMINA_SAVINGS_REMAINDER);
        }
        return paidCost;
    }

    // ─── 事件选择 ───

    private ExplorationEvent selectEvent(List<ExplorationEvent> events, int storyPity,
                                         Map<String, Object> eventRates, double storyEventBonus) {
        return selectEvent(events, storyPity, eventRates, storyEventBonus, new Random());
    }

    static ExplorationEvent selectEvent(List<ExplorationEvent> events, int storyPity,
                                        Map<String, Object> eventRates, double storyEventBonus,
                                        Random random) {
        List<ExplorationEvent> selectionEvents = events;
        // 硬保底只排除非主线事件；主线之间仍按配置权重随机，避免依赖数据库返回顺序。
        if (storyPity >= 8) {
            List<ExplorationEvent> availableStories = events.stream()
                    .filter(e -> "story".equals(e.getEventType()))
                    .toList();
            if (!availableStories.isEmpty()) selectionEvents = availableStories;
        }

        // 按 event_rates_json 进行加权: base_weight × type_rate_bonus
        double totalWeight = 0.0;
        Map<String, Double> adjustedWeights = new LinkedHashMap<>();
        for (ExplorationEvent e : selectionEvents) {
            int w = e.getWeight();
            // 应用 event_rates 倍率 (event_rates 是百分比基准, 100 = 1x)
            if (eventRates.containsKey(e.getEventType())) {
                double rateFactor = ((Number) eventRates.get(e.getEventType())).doubleValue() / 100.0;
                w = Math.max(1, (int) Math.round(w * rateFactor));
            }
            // storyPity 加成
            if ("story".equals(e.getEventType())) {
                w = applyStoryPityToWeight(w, storyPity);
            }
            double adjustedWeight = w;
            if ("story".equals(e.getEventType())) {
                adjustedWeight = applyStoryEventBonusToWeight(w, storyEventBonus);
            }
            adjustedWeights.put(e.getEventKey(), adjustedWeight);
            totalWeight += adjustedWeight;
        }

        if (totalWeight <= 0) return selectionEvents.get(0);

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (ExplorationEvent e : selectionEvents) {
            cumulative += adjustedWeights.get(e.getEventKey());
            if (roll < cumulative) {
                return e;
            }
        }
        return selectionEvents.get(selectionEvents.size() - 1);
    }

    static double applyStoryEventBonusToWeight(double weight, double bonus) {
        if (!Double.isFinite(weight) || weight <= 0.0) return 0.0;
        double safeBonus = Double.isFinite(bonus) ? Math.max(-0.99, bonus) : 0.0;
        return Math.max(0.01, weight * (1.0 + safeBonus));
    }

    static int applyStoryPityToWeight(int weight, int storyPity) {
        if (weight <= 0) return 0;
        long safePity = Math.max(0, storyPity);
        long bonusPerMiss = Math.max(1, weight / 2);
        return (int) Math.min(Integer.MAX_VALUE, weight + safePity * bonusPerMiss);
    }

    // ─── 低等级地点未完成剧情检测 ───

    private List<String> findLowerUnfinishedStories(Location currentLocation,
                                                     Map<String, Object> stageProgress,
                                                     List<?> decisionHistory) {
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

        List<String> triggeredEvents = toStringList(stageProgress.get("storyEventsTriggered"));

        List<String> result = new ArrayList<>();
        for (Location loc : lowerLocations) {
            QueryWrapper<ExplorationEvent> evQw = new QueryWrapper<>();
            evQw.eq("location_key", loc.getLocationKey())
               .eq("event_type", "story")
               .eq("enabled", 1);
            List<ExplorationEvent> storyEvents = eventMapper.selectList(evQw);

            long unfinishedCount = storyEvents.stream()
                    // A branch ruled out by this player's recorded choices is not unfinished work.
                    // Counting it here can permanently block every higher-location story.
                    .filter(e -> !triggeredEvents.contains(e.getEventKey()))
                    .filter(e -> !choiceHistoryPermanentlyClosesEvent(
                            parseJsonMap(e.getRequiredConditionsJson()), decisionHistory))
                    .count();

            if (unfinishedCount > 0) {
                result.add(loc.getName() + "(" + unfinishedCount + ")");
            }
        }
        return result;
    }

    // ─── 故事耗尽检测 ───

    private boolean checkStoriesExhausted(List<ExplorationEvent> allEvents,
                                          List<String> triggeredEvents,
                                          List<?> decisionHistory) {
        for (ExplorationEvent e : allEvents) {
            if (!"story".equals(e.getEventType())) continue;
            if (!triggeredEvents.contains(e.getEventKey()) && e.getEnabled() != null && e.getEnabled() == 1
                    && !choiceHistoryPermanentlyClosesEvent(
                            parseJsonMap(e.getRequiredConditionsJson()), decisionHistory)) {
                return false; // 还有未触发的故事事件
            }
        }
        return true;
    }

    /**
     * Returns true only when every valid alternate choice gate has already been decided against.
     * An unchosen prerequisite remains potentially reachable and must not be silently skipped.
     */
    private boolean choiceHistoryPermanentlyClosesEvent(Map<String, Object> conditions,
                                                         List<?> decisionHistory) {
        if (!conditions.containsKey("choice_unlockers")) return false;
        Object unlockersValue = conditions.get("choice_unlockers");
        if (!(unlockersValue instanceof List<?> unlockers) || unlockers.isEmpty()) return true;

        for (Object unlockerValue : unlockers) {
            if (!(unlockerValue instanceof Map<?, ?> unlocker)) continue;
            Object requiredEventValue = unlocker.get("event_key");
            Object requiredIndicesValue = unlocker.get("choice_indices");
            if (!(requiredEventValue instanceof String requiredEventKey)
                    || !(requiredIndicesValue instanceof List<?> requiredIndices)
                    || requiredIndices.isEmpty()) continue;
            boolean sourceDecisionFound = false;
            for (Object decisionValue : decisionHistory) {
                if (!(decisionValue instanceof Map<?, ?> decision)) continue;
                Object decisionEventKey = decision.containsKey("event_key")
                        ? decision.get("event_key") : decision.get("eventKey");
                if (!requiredEventKey.equals(decisionEventKey)) continue;
                sourceDecisionFound = true;
                Object choiceIndexValue = decision.containsKey("choice_index")
                        ? decision.get("choice_index") : decision.get("choiceIndex");
                int chosenIndex = toInt(choiceIndexValue, -1);
                if (requiredIndices.stream().anyMatch(index -> toInt(index, -2) == chosenIndex)) {
                    return false;
                }
            }
            if (!sourceDecisionFound) return false;
        }
        // No valid gate, or every gate was decided against, means the event cannot be reached.
        return true;
    }

    // ─── 条件检查 ───

    @SuppressWarnings("unchecked")
    private boolean checkRequiredConditions(Map<String, Object> stats,
                                            Map<String, Object> stageProgress,
                                            List<?> decisionHistory,
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
        // required_side_events: optional branches may require previously discovered side-story evidence.
        if (conditions.containsKey("required_side_events")) {
            Object requiredValue = conditions.get("required_side_events");
            Object triggeredValue = stageProgress.get("sideEventsTriggered");
            if (!(requiredValue instanceof List<?> requiredSideEvents) || requiredSideEvents.isEmpty()
                    || !(triggeredValue instanceof List<?> triggeredSideEvents)
                    || !triggeredSideEvents.containsAll(requiredSideEvents)) {
                return false;
            }
        }
        // required_rank
        if (conditions.containsKey("required_rank")) {
            String required = String.valueOf(conditions.get("required_rank"));
            String rank = String.valueOf(stats.getOrDefault("avatarRank", "F"));
            Integer currentOrder = AVATAR_RANK_ORDER.get(rank.trim().toUpperCase(Locale.ROOT));
            Integer requiredOrder = AVATAR_RANK_ORDER.get(required.trim().toUpperCase(Locale.ROOT));
            if (currentOrder == null || requiredOrder == null || currentOrder < requiredOrder) return false;
        }
        // choice_unlockers are alternate personal routes: any recorded matching choice unlocks the event.
        if (conditions.containsKey("choice_unlockers")) {
            Object choiceUnlockersValue = conditions.get("choice_unlockers");
            if (!(choiceUnlockersValue instanceof List<?> choiceUnlockers) || choiceUnlockers.isEmpty()) {
                return false;
            }
            boolean unlocked = false;
            for (Object unlockerValue : choiceUnlockers) {
                if (!(unlockerValue instanceof Map<?, ?> unlocker)) continue;
                Object requiredEventValue = unlocker.get("event_key");
                Object requiredChoiceIndicesValue = unlocker.get("choice_indices");
                if (!(requiredEventValue instanceof String requiredEventKey)
                        || !(requiredChoiceIndicesValue instanceof List<?> requiredChoiceIndices)) continue;
                for (Object decisionValue : decisionHistory) {
                    if (!(decisionValue instanceof Map<?, ?> decision)) continue;
                    Object decisionEventKey = decision.containsKey("event_key")
                            ? decision.get("event_key") : decision.get("eventKey");
                    Object choiceIndexValue = decision.containsKey("choice_index")
                            ? decision.get("choice_index") : decision.get("choiceIndex");
                    if (!requiredEventKey.equals(decisionEventKey)) continue;
                    int chosenIndex = toInt(choiceIndexValue, -1);
                    if (requiredChoiceIndices.stream().anyMatch(index -> toInt(index, -2) == chosenIndex)) {
                        unlocked = true;
                        break;
                    }
                }
                if (unlocked) break;
            }
            if (!unlocked) return false;
        }
        return true;
    }

    // ─── 选项处理 ───

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> makeChoice(Long playerId, String eventKey, int choiceIndex,
                                          String requestKey, Long userId) {
        return makeChoice(playerId, eventKey, choiceIndex, requestKey, null, userId);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> makeChoice(Long playerId, String eventKey, int choiceIndex,
                                          String requestKey, String storyInstanceKey, Long userId) {
        if (requestKey == null || requestKey.isBlank())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 不能为空");
        if (requestKey.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 长度不能超过 64");
        if (storyInstanceKey != null && storyInstanceKey.isBlank()) storyInstanceKey = null;
        if (storyInstanceKey != null && storyInstanceKey.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "storyInstanceKey 长度不能超过 64");

        // 锁定最新玩家进度后再检查一次性剧情标记；并发请求会排队读取前一笔事务的结果。
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> receipt = playerLogService.findStoryChoiceReceipt(playerId, requestKey);
        if (receipt != null) return replayStoryChoiceReceipt(
                eventKey, choiceIndex, requestKey, storyInstanceKey, receipt);

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

        Map<String, Object> pendingSnapshots = (Map<String, Object>) stageProgress.get("pendingStorySnapshots");
        if (storyInstanceKey != null) {
            Object snapshotValue = pendingSnapshots == null ? null : pendingSnapshots.get(eventKey);
            if (!(snapshotValue instanceof Map<?, ?> snapshot)
                    || !Objects.equals(snapshot.get("instanceKey"), storyInstanceKey)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该剧情已结束或不再是当前待选剧情");
            }
        }

        // 一次性剧情的路线历史同时也是领奖幂等标记，必须在应用选项奖励前检查。
        if (event.getRepeatable() != null && event.getRepeatable() == 0) {
            List<String> completedRoutes = (List<String>) stageProgress.getOrDefault(
                    "routeHistory", new ArrayList<>());
            if (completedRoutes.contains(eventKey))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该剧情已经完成，不能重复选择");
        }

        Map<String, Object> chosen = choices.get(choiceIndex);

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event_key", eventKey);
        result.put("event_name", event.getName());
        result.put("choice_index", choiceIndex);
        result.put("choice_label", chosen.get("label"));
        result.put("consequence_text", chosen.getOrDefault("consequence_text", ""));
        if (storyInstanceKey != null) result.put("story_instance_key", storyInstanceKey);

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> statsBeforeChoice = new LinkedHashMap<>(stats);
        int coinsBeforeChoice = toInt(player.getCoins(), 0);
        int fragmentsBeforeChoice = toInt(player.getStoryFragments(), 0);
        // 应用选项覆盖奖励
        Map<String, Object> rewardsOverride = null;
        if (chosen.containsKey("rewards_override") && chosen.get("rewards_override") instanceof Map) {
            rewardsOverride = (Map<String, Object>) chosen.get("rewards_override");
            validateChoiceResourceCosts(player, rewardsOverride);
            Map<String, Object> applied = applyChoiceRewards(player, stats, rewardsOverride);
            result.put("rewards", applied);
        } else {
            result.put("rewards", new LinkedHashMap<>());
        }

        Map<String, Object> choiceRisks = chosen.get("risks") instanceof Map<?, ?> risks
                ? (Map<String, Object>) risks : Collections.emptyMap();
        Map<String, Object> appliedRisks = applyChoiceRisks(stats, choiceRisks);
        if (!appliedRisks.isEmpty()) result.put("applied_risks", appliedRisks);

        Map<String, Object> pendingStoryBattle = buildPendingStoryBattle(
                event, eventKey, choiceIndex, storyInstanceKey, chosen, choiceRisks);
        if (pendingStoryBattle != null) result.put("pending_battle", pendingStoryBattle);
        @SuppressWarnings("unchecked")
        Map<String, Object> appliedChoiceRewards = result.get("rewards") instanceof Map<?, ?> rewardMap
                ? (Map<String, Object>) rewardMap : Collections.emptyMap();
        recordActionResourceChanges(player, "story_choice", eventKey, requestKey,
                coinsBeforeChoice, fragmentsBeforeChoice, statsBeforeChoice, stats, appliedChoiceRewards);
        // Story reward/branch, configured risk damage, and the durable battle receipt are committed
        // in the same transaction. A request replay returns the receipt instead of applying them again.
        player.setStatsJson(serializePlayerState(stats));

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
        Map<String, Object> storyFlagsUpdated = new LinkedHashMap<>();
        Object configuredStoryFlags = chosen.get("set_story_flags");
        if (configuredStoryFlags != null) {
            if (!(configuredStoryFlags instanceof Map<?, ?> configuredFlags)) {
                throw new IllegalStateException("剧情选项 set_story_flags 必须是对象");
            }
            for (Map.Entry<?, ?> entry : configuredFlags.entrySet()) {
                if (!(entry.getKey() instanceof String flag)
                        || !flag.matches("(?:ending|story)_[a-z0-9_]{1,90}")
                        || !(entry.getValue() instanceof Boolean value)) {
                    throw new IllegalStateException("剧情选项只能写入 ending_ 或 story_ 前缀的布尔旗标");
                }
                playerFlags.put(flag, value);
                storyFlagsUpdated.put(flag, value);
            }
        }
        if (!storyFlagsUpdated.isEmpty()) {
            result.put("story_flags_updated", storyFlagsUpdated);
        }
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
        player.setDecisionHistoryJson(serializePlayerState(decisionHistory));

        // ── P0: 记录路线历史 (防重复领奖) ──
        @SuppressWarnings("unchecked")
        List<String> routeHistory = (List<String>) stageProgress.computeIfAbsent(
                "routeHistory", k -> new ArrayList<>());
        if (!routeHistory.contains(eventKey)) {
            routeHistory.add(eventKey);
        }

        if (pendingSnapshots != null) {
            pendingSnapshots.remove(eventKey);
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
        Map<String, Object> outcomeSnapshot = new LinkedHashMap<>();
        outcomeSnapshot.put("snapshot_version", 2);
        outcomeSnapshot.put("applied_rewards", result.getOrDefault("rewards", Map.of()));
        outcomeSnapshot.put("applied_risks", result.getOrDefault("applied_risks", Map.of()));
        outcomeSnapshot.put("unlocked_locations", result.getOrDefault("unlock_location_names", List.of()));
        outcomeSnapshot.put("unlock_events", result.getOrDefault("unlock_events", List.of()));
        outcomeSnapshot.put("story_flags_updated", storyFlagsUpdated);
        outcomeSnapshot.put("pending_battle", pendingStoryBattle != null);
        try {
            log.setRewardsSnapshot(objectMapper.writeValueAsString(outcomeSnapshot));
        } catch (Exception exception) {
            throw new IllegalStateException("剧情选择结果快照序列化失败", exception);
        }
        if (storyLogMapper.insert(log) <= 0) {
            throw new IllegalStateException("剧情选择日志保存失败");
        }

        // ── P0: 章节/阶段推进 ──
        advanceChapter(player, stageProgress, event);

        // 保存玩家
        player.setStageProgressJson(serializePlayerState(stageProgress));
        player.setStoryFlagsJson(serializePlayerState(playerFlags));
        if (playerMapper.updateById(player) <= 0) {
            throw new IllegalStateException("剧情选择结算失败，玩家进度未保存");
        }

        // 评估称号
        List<PlayerTitleVO> newTitles = titleService.evaluateAndUnlockTitles(playerId, null);
        result.put("new_titles", newTitles);

        Map<String, Object> choiceLogMetadata = new LinkedHashMap<>();
        choiceLogMetadata.put("kind", "story_choice");
        choiceLogMetadata.put("eventKey", eventKey);
        choiceLogMetadata.put("choiceIndex", choiceIndex);
        choiceLogMetadata.put("choiceLabel", chosen.get("label"));
        choiceLogMetadata.put("consequenceText", chosen.getOrDefault("consequence_text", ""));
        choiceLogMetadata.put("rewards", result.getOrDefault("rewards", Map.of()));
        choiceLogMetadata.put("appliedRisks", result.getOrDefault("applied_risks", Map.of()));
        choiceLogMetadata.put("unlockLocations", result.getOrDefault("unlock_location_names", List.of()));
        choiceLogMetadata.put("unlockEvents", result.getOrDefault("unlock_events", List.of()));
        choiceLogMetadata.put("storyFlagsUpdated", storyFlagsUpdated);
        choiceLogMetadata.put("newTitles", newTitles);
        if (pendingStoryBattle != null) {
            choiceLogMetadata.put("battleRequestKey", pendingStoryBattle.get("request_key"));
            choiceLogMetadata.put("battleEncounterResolved", false);
        }
        result.put("request_key", requestKey);
        result.put("request_replayed", false);
        choiceLogMetadata.put("requestKey", requestKey);
        if (storyInstanceKey != null) choiceLogMetadata.put("storyInstanceKey", storyInstanceKey);
        Map<String, Object> receiptResponse = new LinkedHashMap<>(result);
        choiceLogMetadata.put("requestResponse", receiptResponse);
        ensureReceiptSerializable(choiceLogMetadata);
        playerLogService.addLog(playerId, "story", "你选择了: " + chosen.get("label"), choiceLogMetadata);

        return result;
    }

    private Map<String, Object> applyChoiceRisks(Map<String, Object> stats, Map<String, Object> risks) {
        Object configuredPercent = risks.get("damagePercent");
        if (configuredPercent == null) return Collections.emptyMap();
        if (!(configuredPercent instanceof Number number))
            throw new IllegalStateException("剧情风险 damagePercent 必须是数字");
        double percent = number.doubleValue();
        if (!Double.isFinite(percent) || percent < 0 || percent > 100)
            throw new IllegalStateException("剧情风险 damagePercent 必须在 0 到 100 之间");
        if (percent == 0) return Collections.emptyMap();

        int maxHp = Math.max(1, toInt(stats.get("maxHp"), 100));
        int hpBefore = Math.max(0, toInt(stats.get("hp"), maxHp));
        int configuredDamage = (int) Math.round(maxHp * percent / 100.0);
        int actualDamage = Math.min(configuredDamage, Math.max(0, hpBefore - 1));
        int hpAfter = hpBefore - actualDamage;
        stats.put("hp", hpAfter);

        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("damagePercent", percent);
        applied.put("damage", actualDamage);
        applied.put("hpBefore", hpBefore);
        applied.put("hpAfter", hpAfter);
        applied.put("nonLethal", true);
        return applied;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPendingStoryBattle(ExplorationEvent event, String eventKey,
                                                        int choiceIndex, String storyInstanceKey,
                                                        Map<String, Object> chosen,
                                                        Map<String, Object> risks) {
        Object configuredMonsterKey = risks.get("monsterKey");
        if (configuredMonsterKey == null) return null;
        if (!(configuredMonsterKey instanceof String monsterKey)
                || monsterKey.isBlank() || monsterKey.length() > 100) {
            throw new IllegalStateException("剧情风险 monsterKey 配置无效");
        }

        String battleName = risks.get("battleName") instanceof String configuredName
                && !configuredName.isBlank() ? configuredName : event.getName();
        String battleDescription = risks.get("battleDescription") instanceof String configuredDescription
                && !configuredDescription.isBlank()
                ? configuredDescription
                : Objects.toString(chosen.get("consequence_text"), "");
        String battleRequestKey = UUID.randomUUID().toString();

        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("event_key", eventKey + "_choice_" + choiceIndex);
        encounter.put("event_type", "story_battle");
        encounter.put("name", battleName);
        encounter.put("description", battleDescription);

        Map<String, Object> pendingBattle = new LinkedHashMap<>();
        pendingBattle.put("result_type", "battle");
        pendingBattle.put("monster_key", monsterKey);
        String locationKey = event.getLocationKey();
        if (locationKey != null && !locationKey.isBlank()) {
            pendingBattle.put("location_key", locationKey);
            QueryWrapper<Location> locationQuery = new QueryWrapper<>();
            locationQuery.eq("location_key", locationKey);
            Location location = locationMapper.selectOne(locationQuery);
            if (location != null && location.getName() != null && !location.getName().isBlank()) {
                pendingBattle.put("location_name", location.getName());
            }
        }
        pendingBattle.put("result", encounter);
        pendingBattle.put("rewards", Collections.emptyMap());
        pendingBattle.put("progress_effects", Collections.emptyMap());
        pendingBattle.put("new_titles", Collections.emptyList());
        pendingBattle.put("stories_exhausted", false);
        pendingBattle.put("new_logs", Collections.emptyList());
        pendingBattle.put("request_key", battleRequestKey);
        pendingBattle.put("request_replayed", false);
        if (storyInstanceKey != null) pendingBattle.put("story_instance_key", storyInstanceKey);
        return pendingBattle;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> replayStoryChoiceReceipt(String eventKey, int choiceIndex,
                                                         String requestKey, String storyInstanceKey,
                                                         Map<String, Object> receipt) {
        if (!Objects.equals(receipt.get("eventKey"), eventKey)
                || toInt(receipt.get("choiceIndex"), -1) != choiceIndex
                || !Objects.equals(receipt.get("storyInstanceKey"), storyInstanceKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该请求键已用于其他剧情实例或选项，请重新进入当前剧情后再试");
        }
        Object storedResponse = receipt.get("requestResponse");
        if (!(storedResponse instanceof Map<?, ?> response)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "剧情选择回执缺失，已阻止重复结算");
        }
        Map<String, Object> replay = new LinkedHashMap<>((Map<String, Object>) response);
        replay.put("request_key", requestKey);
        replay.put("request_replayed", true);
        if (storyInstanceKey != null) replay.put("story_instance_key", storyInstanceKey);
        return replay;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPendingStories(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的剧情");

        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        List<String> triggered = (List<String>) stageProgress.getOrDefault(
                "storyEventsTriggered", Collections.emptyList());
        List<String> completedRoutes = (List<String>) stageProgress.getOrDefault(
                "routeHistory", Collections.emptyList());
        if (triggered.isEmpty()) return Collections.emptyList();

        Map<String, Object> pendingSnapshots = (Map<String, Object>) stageProgress.getOrDefault(
                "pendingStorySnapshots", Collections.emptyMap());
        QueryWrapper<PlayerStoryLog> completedQuery = new QueryWrapper<>();
        completedQuery.eq("player_id", playerId).isNotNull("choice_index");
        List<PlayerStoryLog> completedLogs = storyLogMapper.selectList(completedQuery);
        Set<String> completedEventKeys = new HashSet<>();
        if (completedLogs != null) {
            for (PlayerStoryLog log : completedLogs) {
                if (log.getEventKey() != null) completedEventKeys.add(log.getEventKey());
            }
        }

        LinkedHashSet<String> candidateKeys = new LinkedHashSet<>(triggered);
        if (candidateKeys.isEmpty()) return Collections.emptyList();

        QueryWrapper<ExplorationEvent> eventQuery = new QueryWrapper<>();
        eventQuery.in("event_key", candidateKeys);
        List<ExplorationEvent> events = eventMapper.selectList(eventQuery);
        if (events == null || events.isEmpty()) return Collections.emptyList();

        Map<String, ExplorationEvent> eventByKey = new HashMap<>();
        for (ExplorationEvent event : events) eventByKey.put(event.getEventKey(), event);

        List<Map<String, Object>> results = new ArrayList<>();
        for (String eventKey : candidateKeys) {
            ExplorationEvent event = eventByKey.get(eventKey);
            if (event == null || !"story".equals(event.getEventType())) continue;
            boolean oneTime = event.getRepeatable() != null && event.getRepeatable() == 0;
            if (oneTime && (completedRoutes.contains(eventKey) || completedEventKeys.contains(eventKey))) continue;
            // 可重复剧情的触发标记是永久性的；只恢复本次探索实际保存下来的待选快照。
            if (!oneTime && !pendingSnapshots.containsKey(eventKey)) continue;
            Object parsedChoices = parseJsonList(event.getChoicesJson());
            if (!(parsedChoices instanceof List<?> choices) || choices.isEmpty()) continue;

            Object snapshotValue = pendingSnapshots.get(eventKey);
            Map<String, Object> snapshot = snapshotValue instanceof Map
                    ? (Map<String, Object>) snapshotValue : Collections.emptyMap();
            List<String> personalContexts = snapshot.containsKey("personal_contexts")
                    ? toStringList(snapshot.get("personal_contexts"))
                    : resolvePersonalContexts(event, player);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("result_type", "story");
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("event_key", event.getEventKey());
            eventData.put("event_type", event.getEventType());
            eventData.put("name", event.getName());
            eventData.put("description", event.getDescription());
            eventData.put("choices", choices);
            if (!personalContexts.isEmpty()) eventData.put("personal_contexts", personalContexts);
            result.put("result", eventData);

            result.put("rewards", snapshot.getOrDefault("rewards", Collections.emptyMap()));
            result.put("new_titles", snapshot.getOrDefault("new_titles", Collections.emptyList()));
            if (snapshot.get("instanceKey") instanceof String instanceKey && !instanceKey.isBlank()) {
                result.put("story_instance_key", instanceKey);
            }
            result.put("progress_effects", Collections.emptyMap());
            result.put("stories_exhausted", false);
            result.put("new_logs", Collections.emptyList());
            results.add(result);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> getPendingBattles(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的战斗");
        return playerLogService.findPendingBattleEncounters(playerId);
    }

    @Override
    public void acknowledgePendingBattle(Long playerId, String requestKey, Long userId) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 必须为 1 到 64 个字符");
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权确认此玩家的战斗");
        playerLogService.acknowledgeBattleEncounter(playerId, requestKey);
    }

    private void validateChoiceResourceCosts(Player player, Map<String, Object> rewards) {
        validateChoiceResourceCost("星币", player.getCoins(), rewards.get("coins"));
        validateChoiceResourceCost("故事碎片", player.getStoryFragments(), rewards.get("storyFragments"));
    }

    private void validateChoiceResourceCost(String resourceName, Integer currentBalance, Object rewardValue) {
        int delta = toInt(rewardValue, 0);
        if (delta >= 0) return;

        long cost = -(long) delta;
        long available = currentBalance == null ? 0 : currentBalance.longValue();
        if (available < cost) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    resourceName + "不足，无法选择该选项（需要 " + cost + "，当前 " + available + "）");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyChoiceRewards(Player player, Map<String, Object> stats,
                                                   Map<String, Object> rewards) {
        Map<String, Object> applied = new LinkedHashMap<>();
        // coins
        if (rewards.containsKey("coins")) {
            int add = toInt(rewards.get("coins"), 0);
            player.setCoins(ResourceBalance.applyDelta("星币", player.getCoins(), add));
            applied.put("coins", add);
        }
        // storyFragments
        if (rewards.containsKey("storyFragments")) {
            int add = toInt(rewards.get("storyFragments"), 0);
            player.setStoryFragments(ResourceBalance.applyDelta("故事碎片", player.getStoryFragments(), add));
            applied.put("storyFragments", add);
        }
        // channelHeat
        if (rewards.containsKey("channelHeat")) {
            int add = playerEffectService.applyChannelHeatGainRate(
                    player.getId(), toInt(rewards.get("channelHeat"), 0));
            int cur = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", ResourceBalance.applySignedDelta("星流热度", cur, add));
            applied.put("channelHeat", add);
        }
        // worldLineShift (存储在 stats_json 中)
        if (rewards.containsKey("worldLineShift")) {
            int add = toInt(rewards.get("worldLineShift"), 0);
            int current = toInt(stats.get("worldLineShift"), 0);
            stats.put("worldLineShift", ResourceBalance.applySignedDelta("世界线偏移", current, add));
            applied.put("worldLineShift", add);
        }
        // exp
        if (rewards.containsKey("exp")) {
            int add = toInt(rewards.get("exp"), 0);
            double multiplier = ExperienceProgression.multiplierForRank(
                    (String) stats.getOrDefault("avatarRank", "F"));
            ExperienceProgression.Result progression = ExperienceProgression.addExperience(stats, add, multiplier);
            if (progression.experienceAwarded() > 0) applied.put("exp", progression.experienceAwarded());
            writeLevelUpLog(player.getId(), progression);
        }
        // items
        if (rewards.containsKey("items") && rewards.get("items") instanceof List) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) rewards.get("items");
            List<Map<String, Object>> acquired = new ArrayList<>();
            for (Map<String, Object> drop : items) {
                String itemKey = (String) drop.get("itemKey");
                if (itemKey == null) continue;
                int qty = toInt(drop.get("quantity"), 1);
                var grant = inventoryService.addItemWithResult(player.getId(), itemKey, qty);
                if (grant.quantityGranted() > 0) {
                    Map<String, Object> acquiredItem = new LinkedHashMap<>();
                    acquiredItem.put("itemKey", itemKey);
                    acquiredItem.put("quantity", grant.quantityGranted());
                    acquiredItem.put("totalQuantity", grant.totalQuantity());
                    if (grant.itemName() != null && !grant.itemName().isBlank()) {
                        acquiredItem.put("itemName", grant.itemName());
                    }
                    acquired.add(acquiredItem);
                }
            }
            if (!acquired.isEmpty()) applied.put("items", acquired);
        }
        // equipment
        if (rewards.containsKey("equipment") && rewards.get("equipment") instanceof List) {
            List<Map<String, Object>> equips = (List<Map<String, Object>>) rewards.get("equipment");
            List<Map<String, Object>> acquiredEquip = new ArrayList<>();
            Map<String, String> equipmentNames = equipmentNameLookup();
            for (Map<String, Object> drop : equips) {
                String equipKey = (String) drop.get("equipmentKey");
                if (equipKey == null) continue;
                int r = equipmentService.addEquipment(player.getId(), equipKey, "story_choice");
                if (r > 0) {
                    Map<String, Object> acquired = new LinkedHashMap<>();
                    acquired.put("equipmentKey", equipKey);
                    String equipmentName = equipmentNames.get(equipKey);
                    if (equipmentName != null) acquired.put("equipmentName", equipmentName);
                    acquiredEquip.add(acquired);
                }
            }
            if (!acquiredEquip.isEmpty()) applied.put("equipment", acquiredEquip);
        }
        return applied;
    }

    private Map<String, String> equipmentNameLookup() {
        List<EquipmentVO> definitions = equipmentService.getAllEquipment();
        if (definitions == null || definitions.isEmpty()) return Collections.emptyMap();

        Map<String, String> namesByKey = new HashMap<>();
        for (EquipmentVO definition : definitions) {
            if (definition == null || definition.getEquipmentKey() == null
                    || definition.getName() == null || definition.getName().isBlank()) continue;
            namesByKey.putIfAbsent(definition.getEquipmentKey(), definition.getName());
        }
        return namesByKey;
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
            Map<String, Object> outcomeSnapshot = parseJsonMap(log.getRewardsSnapshot());
            if (toInt(outcomeSnapshot.get("snapshot_version"), 0) == 2) {
                entry.put("rewards", outcomeSnapshot.getOrDefault("applied_rewards", Collections.emptyMap()));
                entry.put("applied_outcomes", outcomeSnapshot);
            } else {
                // Older rows contain the configured reward definition, not proof of what was granted.
                entry.put("rewards", parseJsonList(log.getRewardsSnapshot()));
            }
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
            player.setCoins(ResourceBalance.applyDelta("星币", player.getCoins(), add));
            applied.put("coins", add);
        }

        // storyFragments
        if (rewards.containsKey("storyFragments")) {
            int add = toInt(rewards.get("storyFragments"), 0);
            player.setStoryFragments(ResourceBalance.applyDelta("故事碎片", player.getStoryFragments(), add));
            applied.put("storyFragments", add);
        }

        // channelHeat (存储在 stats_json 中)
        if (rewards.containsKey("channelHeat")) {
            int add = playerEffectService.applyChannelHeatGainRate(
                    player.getId(), toInt(rewards.get("channelHeat"), 0));
            int current = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", ResourceBalance.applySignedDelta("星流热度", current, add));
            applied.put("channelHeat", add);
        }

        // worldLineShift (存储在 stats_json 中)
        if (rewards.containsKey("worldLineShift")) {
            int add = toInt(rewards.get("worldLineShift"), 0);
            int current = toInt(stats.get("worldLineShift"), 0);
            stats.put("worldLineShift", ResourceBalance.applySignedDelta("世界线偏移", current, add));
            applied.put("worldLineShift", add);
        }

        // exp (位阶倍率加成: 位阶越高经验越多)
        if (rewards.containsKey("exp")) {
            int add = toInt(rewards.get("exp"), 0);
            double multiplier = ExperienceProgression.multiplierForRank(
                    (String) stats.getOrDefault("avatarRank", "F"));
            ExperienceProgression.Result progression = ExperienceProgression.addExperience(stats, add, multiplier);
            if (progression.experienceAwarded() > 0) applied.put("exp", progression.experienceAwarded());
            writeLevelUpLog(player.getId(), progression);
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
                    var grant = inventoryService.addItemWithResult(player.getId(), itemKey, qty);
                    if (grant.quantityGranted() > 0) {
                        Map<String, Object> acquired = new LinkedHashMap<>();
                        acquired.put("itemKey", itemKey);
                        acquired.put("quantity", grant.quantityGranted());
                        acquired.put("totalQuantity", grant.totalQuantity());
                        if (grant.itemName() != null && !grant.itemName().isBlank()) {
                            acquired.put("itemName", grant.itemName());
                        }
                        acquiredItems.add(acquired);
                    }
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
            Map<String, String> equipmentNames = equipmentNameLookup();
            for (Map<String, Object> drop : equipmentDrops) {
                String equipKey = (String) drop.get("equipmentKey");
                if (equipKey == null) continue;
                double dropRate = drop.containsKey("dropRate") ?
                        ((Number) drop.get("dropRate")).doubleValue() : 1.0;
                if (Math.random() < dropRate) {
                    int result = equipmentService.addEquipment(player.getId(), equipKey, "exploration");
                    if (result > 0) {
                        Map<String, Object> acquired = new LinkedHashMap<>();
                        acquired.put("equipmentKey", equipKey);
                        String equipmentName = equipmentNames.get(equipKey);
                        if (equipmentName != null) acquired.put("equipmentName", equipmentName);
                        acquiredEquip.add(acquired);
                    }
                }
            }
            if (!acquiredEquip.isEmpty()) {
                applied.put("equipment", acquiredEquip);
            }
        }

        return applied;
    }

    static double combineEffectBonus(Map<String, Object> skillEffects,
                                     Map<String, Object> titleEffects,
                                     Map<String, Object> equipmentEffects,
                                     String effectKey) {
        double combined = finiteNumericEffect(skillEffects, effectKey)
                + finiteNumericEffect(titleEffects, effectKey)
                + finiteNumericEffect(equipmentEffects, effectKey);
        return Double.isFinite(combined) ? combined : 0.0;
    }

    private static double finiteNumericEffect(Map<String, Object> effects, String effectKey) {
        if (effects == null || !(effects.get(effectKey) instanceof Number value)) return 0.0;
        double number = value.doubleValue();
        return Double.isFinite(number) ? number : 0.0;
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

        // 隐藏事件按不同事件标识累计；重复遇到同一事件不能刷高章节进度。
        if ("hidden".equals(event.getEventType())) {
            List<String> hiddenTriggered = (List<String>) stageProgress.computeIfAbsent(
                    "hiddenEventsTriggered", k -> new ArrayList<>());
            if (!hiddenTriggered.contains(event.getEventKey())) {
                hiddenTriggered.add(event.getEventKey());
                applied.put("hiddenEventsTriggered", true);
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

    private void writeLevelUpLog(Long playerId, ExperienceProgression.Result progression) {
        String message = ExperienceProgression.levelUpMessage(progression);
        if (message != null) playerLogService.addLog(playerId, "milestone", message);
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

    /**
     * Resolve optional, display-only story echoes for the current player's own timeline.
     * These entries live beside gameplay gates in required_conditions_json, but this method
     * only reads them for presentation; checkRequiredConditions never treats them as gates.
     */
    private List<String> resolvePersonalContexts(ExplorationEvent event, Player player) {
        Map<String, Object> conditions = parseJsonMap(event.getRequiredConditionsJson());
        Object contextsValue = conditions.get("personal_contexts");
        if (!(contextsValue instanceof List<?> contexts) || contexts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> playerFlags = parseJsonMap(player.getStoryFlagsJson());
        List<String> resolved = new ArrayList<>();
        for (Object contextValue : contexts) {
            if (!(contextValue instanceof Map<?, ?> context)) continue;
            Object flagValue = context.get("story_flag");
            Object textValue = context.get("text");
            if (!(flagValue instanceof String flag) || !(textValue instanceof String text)
                    || text.isBlank() || !Boolean.TRUE.equals(playerFlags.get(flag))) {
                continue;
            }
            resolved.add(text);
        }
        return resolved;
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    private long asLong(Object val, long defaultVal) {
        return val instanceof Number number ? number.longValue() : defaultVal;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> entries)) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof String text) result.add(text);
        }
        return result;
    }

    private void recordActionResourceChanges(Player player, String sourceType, String sourceId,
                                             String requestKey, long coinsBefore, long fragmentsBefore,
                                             Map<String, Object> statsBefore,
                                             Map<String, Object> statsAfter,
                                             Map<String, Object> appliedRewards) {
        Long playerId = player.getId();
        int coinsAfter = toInt(player.getCoins(), 0);
        recordResourceChange(playerId, "currency", "coins", coinsBefore, coinsAfter,
                sourceType, sourceId, requestKey + ":coins", requestKey, Map.of());
        int fragmentsAfter = toInt(player.getStoryFragments(), 0);
        recordResourceChange(playerId, "currency", "story_fragments", fragmentsBefore, fragmentsAfter,
                sourceType, sourceId, requestKey + ":story-fragments", requestKey, Map.of());

        recordStatResourceChange(playerId, sourceType, sourceId, requestKey,
                "experience", "exp", statsBefore, statsAfter);
        recordStatResourceChange(playerId, sourceType, sourceId, requestKey,
                "energy", "stamina", statsBefore, statsAfter);
        recordStatResourceChange(playerId, sourceType, sourceId, requestKey,
                "vitality", "hp", statsBefore, statsAfter);
        recordStatResourceChange(playerId, sourceType, sourceId, requestKey,
                "progress", "channelHeat", statsBefore, statsAfter);
        recordStatResourceChange(playerId, sourceType, sourceId, requestKey,
                "progress", "worldLineShift", statsBefore, statsAfter);

        Object itemValue = appliedRewards.get("items");
        if (itemValue instanceof List<?> items) {
            for (int index = 0; index < items.size(); index++) {
                if (!(items.get(index) instanceof Map<?, ?> item)) continue;
                String itemKey = Objects.toString(item.get("itemKey"), "");
                long quantity = asLong(item.get("quantity"), 0);
                long totalQuantity = asLong(item.get("totalQuantity"), -1);
                if (itemKey.isBlank() || quantity <= 0 || totalQuantity < quantity) continue;
                recordResourceChange(playerId, "item", itemKey, totalQuantity - quantity,
                        totalQuantity, sourceType, sourceId,
                        requestKey + ":item:" + index, requestKey, Map.of("granted", quantity));
            }
        }

        Object equipmentValue = appliedRewards.get("equipment");
        if (equipmentValue instanceof List<?> equipment) {
            for (int index = 0; index < equipment.size(); index++) {
                if (!(equipment.get(index) instanceof Map<?, ?> item)) continue;
                String equipmentKey = Objects.toString(item.get("equipmentKey"), "");
                if (equipmentKey.isBlank()) continue;
                recordResourceChange(playerId, "equipment", equipmentKey, 0, 1,
                        sourceType, sourceId, requestKey + ":equipment:" + index, requestKey, Map.of());
            }
        }
    }

    private void recordStatResourceChange(Long playerId, String sourceType, String sourceId,
                                          String requestKey, String resourceType, String resourceKey,
                                          Map<String, Object> statsBefore, Map<String, Object> statsAfter) {
        long before = asLong(statsBefore.get(resourceKey), 0);
        long after = asLong(statsAfter.get(resourceKey), before);
        String ledgerResourceKey = canonicalProgressResourceKey(resourceKey);
        recordResourceChange(playerId, resourceType, ledgerResourceKey, before, after,
                sourceType, sourceId, requestKey + ":" + ledgerResourceKey, requestKey, Map.of());
    }

    private String canonicalProgressResourceKey(String resourceKey) {
        return switch (resourceKey) {
            case "channelHeat" -> "channel_heat";
            case "worldLineShift" -> "world_line_shift";
            default -> resourceKey;
        };
    }

    private void recordResourceChange(Long playerId, String resourceType, String resourceKey,
                                      long balanceBefore, long balanceAfter,
                                      String sourceType, String sourceId, String idempotencyKey,
                                      String transactionGroupId,
                                      Map<String, Object> metadata) {
        long amount = balanceAfter - balanceBefore;
        if (amount == 0) return;
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, balanceBefore, balanceAfter,
                sourceType, sourceId, transactionGroupId, idempotencyKey, metadata));
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
