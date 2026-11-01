package com.huazhenghai.readergame.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.ExploreRequest;
import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.ExplorationEventMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.LocationUnlockPolicy;
import com.huazhenghai.readergame.service.impl.ExploreStoryEligibility;
import com.huazhenghai.readergame.service.impl.RouteContinuationPolicy;
import com.huazhenghai.readergame.service.impl.RouteShareService;
import com.huazhenghai.readergame.service.impl.StoryTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/api/explore")
@Tag(name = "Explore", description = "探索系统接口")
public class ExploreController {

    private final ExploreService exploreService;
    private final LocationMapper locationMapper;
    private final ExplorationEventMapper eventMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;
    private final StoryTreeService storyTreeService;
    private final RouteShareService routeShareService;

    public ExploreController(ExploreService exploreService,
                             LocationMapper locationMapper,
                             ExplorationEventMapper eventMapper,
                             PlayerMapper playerMapper,
                             ObjectMapper objectMapper,
                             StoryTreeService storyTreeService, RouteShareService routeShareService) {
        this.exploreService = exploreService;
        this.locationMapper = locationMapper;
        this.eventMapper = eventMapper;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
        this.storyTreeService = storyTreeService;
        this.routeShareService = routeShareService;
    }

    @GetMapping("/story-tree/{playerId}")
    @Operation(summary = "获取玩家剧情流程图与匿名选择占比")
    public Result<Map<String, Object>> getStoryTree(@PathVariable Long playerId) {
        return Result.ok(storyTreeService.getStoryTree(requireOwnedPlayer(playerId)));
    }

    @GetMapping("/route-codex/{playerId}")
    @Operation(summary = "获取本人路线图鉴与累计到过率")
    public Result<Map<String, Object>> getRouteCodex(@PathVariable Long playerId) {
        return Result.ok(storyTreeService.getRouteCodex(requireOwnedPlayer(playerId)));
    }

    @PostMapping("/route-share/{playerId}")
    @Operation(summary = "生成或复用本人七天路线分享")
    public Result<Map<String, Object>> createRouteShare(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        return Result.ok(routeShareService.create(playerId));
    }

    @DeleteMapping("/route-share/{playerId}")
    @Operation(summary = "撤销本人路线分享")
    public Result<Map<String, Object>> revokeRouteShare(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        routeShareService.revoke(playerId);
        return Result.ok(Collections.emptyMap());
    }

    @GetMapping("/locations/{playerId}")
    @Operation(summary = "获取玩家可探索地点列表 (含故事耗尽状态)")
    public Result<Map<String, Object>> getLocations(@PathVariable Long playerId) {
        Player player = requireOwnedPlayer(playerId);
        List<Location> locations = locationMapper.selectList(null);

        // 获取玩家已触发的故事事件列表
        final List<String> triggered = getTriggeredStoryEvents(player);
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        List<?> decisions = getDecisionHistory(player);
        RouteContinuationPolicy.restoreCompletedFirstGrowthTip(stageProgress, decisions);
        Function<String, Map<String, Object>> ancestors = ancestorConditionsLookup();
        // 解析玩家状态用于解锁检查
        Map<String, Object> playerStats = parseJsonMap(player.getStatsJson());

        List<Map<String, Object>> list = new ArrayList<>();
        for (Location loc : locations) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("location_key", loc.getLocationKey());
            m.put("location_name", loc.getName());
            m.put("description", loc.getDescription());
            m.put("danger_level", loc.getDangerLevel());
            m.put("min_level", loc.getMinLevel());
            m.put("recommended_rank", loc.getRecommendedRank());
            // P0: 四种解锁条件检查 (required_level / prerequisite_events / required_items / chapter_min)
            Map<String, Object> unlockConds = parseJsonMap(loc.getUnlockConditionsJson());
            boolean unlocked = checkUnlockConditions(unlockConds, playerStats, player, triggered);
            m.put("is_unlocked", unlocked);
            // 返回解锁条件详情供前端展示
            if (!unlockConds.isEmpty()) {
                m.put("unlock_conditions", unlockConds);
            }

            // 检查该地点的 story 事件是否全部被触发
            QueryWrapper<ExplorationEvent> evQ = new QueryWrapper<>();
            evQ.eq("location_key", loc.getLocationKey())
               .eq("event_type", "story")
               .eq("enabled", 1);
            List<ExplorationEvent> storyEvents = eventMapper.selectList(evQ);
            boolean exhausted = !storyEvents.isEmpty() && storyEvents.stream()
                    .noneMatch(e -> ExploreStoryEligibility.isUnfinishedStory(e.getEventKey(),
                            parseJsonMap(e.getRequiredConditionsJson()), triggered, decisions,
                            ancestors, stageProgress));
            m.put("stories_exhausted", exhausted);
            m.put("total_stories", storyEvents.size());
            long triggeredCount = storyEvents.stream()
                    .filter(e -> triggered.contains(e.getEventKey())).count();
            m.put("triggered_stories", (int) triggeredCount);

            list.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", list);
        result.put("route_continuation", routeContinuationResponse(player, stageProgress,
                playerStats, locations, triggered, decisions));
        return Result.ok(result);
    }

    @PostMapping("/start")
    @Operation(summary = "开始探索")
    public Result<Map<String, Object>> startExplore(@Valid @RequestBody ExploreRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> result = exploreService.startExplore(
                req.getPlayerId(), req.getLocationKey(), req.getRequestKey(), req.isTraceRoute(), userId);
        return Result.ok(result);
    }

    @GetMapping("/cost-preview/{playerId}")
    @Operation(summary = "预估当前地点单次探索体力消耗区间")
    public Result<Map<String, Object>> getCostPreview(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        return Result.ok(exploreService.getCostPreview(playerId));
    }

    @PostMapping("/choose")
    @Operation(summary = "故事事件中做出选择")
    public Result<Map<String, Object>> makeChoice(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = body.get("playerId") instanceof Number
                ? ((Number) body.get("playerId")).longValue() : null;
        String eventKey = (String) body.get("eventKey");
        String requestKey = body.get("requestKey") instanceof String
                ? (String) body.get("requestKey") : null;
        String storyInstanceKey = body.get("storyInstanceKey") instanceof String
                ? (String) body.get("storyInstanceKey") : null;
        int choiceIndex = body.get("choiceIndex") instanceof Number
                ? ((Number) body.get("choiceIndex")).intValue() : -1;

        if (playerId == null || eventKey == null || choiceIndex < 0
                || requestKey == null || requestKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不完整");
        }
        Map<String, Object> result = exploreService.makeChoice(
                playerId, eventKey, choiceIndex, requestKey, storyInstanceKey, userId);
        return Result.ok(result);
    }

    @GetMapping("/pending-stories/{playerId}")
    @Operation(summary = "获取尚未完成选择的剧情")
    public Result<Map<String, Object>> getPendingStories(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending_stories", exploreService.getPendingStories(playerId, userId));
        return Result.ok(result);
    }

    @GetMapping("/pending-battles/{playerId}")
    @Operation(summary = "获取尚未完成的战斗遭遇")
    public Result<Map<String, Object>> getPendingBattles(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending_battles", exploreService.getPendingBattles(playerId, userId));
        return Result.ok(result);
    }

    @PostMapping("/pending-battles/{playerId}/ack")
    @Operation(summary = "确认战斗遭遇已完成")
    public Result<Map<String, Object>> acknowledgePendingBattle(
            @PathVariable Long playerId, @RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        String requestKey = body.get("requestKey") instanceof String value ? value : null;
        exploreService.acknowledgePendingBattle(playerId, requestKey, userId);
        return Result.ok(Collections.emptyMap());
    }

    @GetMapping("/map-nodes/{playerId}")
    @Operation(summary = "获取场景地图节点数据 (替代前端 mockData)")
    public Result<Map<String, Object>> getMapNodes(@PathVariable Long playerId) {
        Player player = requireOwnedPlayer(playerId);
        List<Location> locations = locationMapper.selectList(null);
        final List<String> triggered = getTriggeredStoryEvents(player);
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        List<?> decisions = getDecisionHistory(player);
        RouteContinuationPolicy.restoreCompletedFirstGrowthTip(stageProgress, decisions);
        Function<String, Map<String, Object>> ancestors = ancestorConditionsLookup();
        Map<String, Object> playerStats = parseJsonMap(player.getStatsJson());

        // 地图元数据：每个位置在地图上的坐标和连线
        Map<String, Map<String, Object>> mapMeta = buildMapMeta();

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Location loc : locations) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", loc.getLocationKey());
            node.put("name", loc.getName());
            node.put("description", loc.getDescription());
            node.put("minLevel", loc.getMinLevel());
            node.put("dangerLevel", loc.getDangerLevel());
            node.put("recommendedRank", loc.getRecommendedRank());

            // 解锁检查
            Map<String, Object> conds = parseJsonMap(loc.getUnlockConditionsJson());
            boolean unlocked = checkUnlockConditions(conds, playerStats, player, triggered);
            node.put("unlocked", unlocked);

            // 故事耗尽检查
            QueryWrapper<ExplorationEvent> evQ = new QueryWrapper<>();
            evQ.eq("location_key", loc.getLocationKey()).eq("event_type", "story").eq("enabled", 1);
            List<ExplorationEvent> storyEvents = eventMapper.selectList(evQ);
            boolean exhausted = !storyEvents.isEmpty() && storyEvents.stream()
                    .noneMatch(e -> ExploreStoryEligibility.isUnfinishedStory(e.getEventKey(),
                            parseJsonMap(e.getRequiredConditionsJson()), triggered, decisions,
                            ancestors, stageProgress));
            node.put("completed", exhausted);
            node.put("totalStories", storyEvents.size());
            node.put("triggeredStories", (int) storyEvents.stream()
                    .filter(e -> triggered.contains(e.getEventKey())).count());

            // 从 mapMeta 补充坐标/类型/卷/连线
            Map<String, Object> meta = mapMeta.get(loc.getLocationKey());
            if (meta != null) {
                node.put("x", meta.getOrDefault("x", 400));
                node.put("y", meta.getOrDefault("y", 400));
                node.put("type", meta.getOrDefault("type", "side"));
                node.put("volume", meta.getOrDefault("volume", 1));
                node.put("chapter", meta.getOrDefault("chapter", 1));
                node.put("chapterName", meta.getOrDefault("chapterName", ""));
                node.put("keyItems", meta.getOrDefault("keyItems", new ArrayList<>()));
                node.put("connectedTo", meta.getOrDefault("connectedTo", new ArrayList<>()));
            } else {
                // fallback 默认值
                node.put("x", 400);
                node.put("y", 400);
                node.put("type", "side");
                node.put("volume", 1);
                node.put("chapter", 1);
                node.put("chapterName", "");
                node.put("keyItems", new ArrayList<>());
                node.put("connectedTo", new ArrayList<>());
            }

            nodes.add(node);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("route_continuation", routeContinuationResponse(player, stageProgress,
                playerStats, locations, triggered, decisions));
        return Result.ok(result);
    }

    private Map<String, Map<String, Object>> buildMapMeta() {
        Map<String, Map<String, Object>> meta = new LinkedHashMap<>();
        meta.put("ruined_station", nodeMeta(400, 480, "main", 1, 1, "序幕·开始的读者",
            List.of("新手之书", "回归者之证"), List.of("geumho_station", "gwangjin_bridge")));
        meta.put("geumho_station", nodeMeta(580, 380, "main", 1, 2, "第一个避难所",
            List.of("星座碎片", "应急粮食"), List.of("ruined_station", "chungmuro", "dongmyo")));
        meta.put("chungmuro", nodeMeta(720, 300, "boss", 1, 3, "忠武路的统治者",
            List.of("十王之冠", "忠武路地图"), List.of("geumho_station", "myeongdong", "dongdaemun")));
        meta.put("dongmyo", nodeMeta(500, 280, "side", 1, 2, "第一个避难所",
            List.of("情报碎片", "交易币"), List.of("geumho_station", "dongdaemun")));
        meta.put("dongdaemun", nodeMeta(650, 200, "boss", 1, 4, "东大门之战",
            List.of("火焰之心", "东大门令牌"), List.of("dongmyo", "chungmuro", "myeongdong")));
        meta.put("myeongdong", nodeMeta(820, 350, "side", 1, 4, "东大门之战",
            List.of("古老典籍", "技能书页"), List.of("chungmuro", "dongdaemun")));
        meta.put("gwangjin_bridge", nodeMeta(350, 580, "main", 1, 5, "跨越汉江",
            List.of("汉江通行证", "桥之守护者的遗物"), List.of("ruined_station", "gangnam_station")));
        meta.put("gangnam_station", nodeMeta(250, 660, "main", 1, 5, "跨越汉江",
            List.of("地下城钥匙", "江南区地图"), List.of("gwangjin_bridge", "seoul_forest")));
        meta.put("seoul_forest", nodeMeta(150, 700, "hidden", 1, 5, "跨越汉江",
            List.of("世界树苗", "森林精灵之泪"), List.of("gangnam_station")));
        meta.put("demon_gate", nodeMeta(100, 250, "main", 2, 1, "次元裂缝",
            List.of("次元之钥", "恶魔通行证"), List.of("demon_city", "abyss_shore")));
        return meta;
    }

    private Map<String, Object> nodeMeta(int x, int y, String type, int volume, int chapter,
                                         String chapterName, List<String> keyItems, List<String> connectedTo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x); m.put("y", y); m.put("type", type);
        m.put("volume", volume); m.put("chapter", chapter);
        m.put("chapterName", chapterName); m.put("keyItems", keyItems);
        m.put("connectedTo", connectedTo);
        return m;
    }

    @GetMapping("/story-log/{playerId}")
    @Operation(summary = "获取玩家故事回顾日志")
    public Result<Map<String, Object>> getStoryLog(@PathVariable Long playerId) {
        requireOwnedPlayer(playerId);
        List<Map<String, Object>> storyLog = exploreService.getStoryLog(playerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("story_log", storyLog);
        return Result.ok(result);
    }

    private Player requireOwnedPlayer(Long playerId) {
        var loginUser = LoginUserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long userId = loginUser.getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!Objects.equals(player.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权读取此玩家的探索进度");
        }
        return player;
    }

    @SuppressWarnings("unchecked")
    private List<String> getTriggeredStoryEvents(Player player) {
        if (player == null || player.getStageProgressJson() == null) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> sp = objectMapper.readValue(
                    player.getStageProgressJson(), Map.class);
            return (List<String>) sp.getOrDefault("storyEventsTriggered", new ArrayList<>());
        } catch (Exception e) {
            return new ArrayList<>();
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

    private List<?> getDecisionHistory(Player player) {
        try {
            return objectMapper.readValue(player.getDecisionHistoryJson(), List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> routeContinuationResponse(Player player,
            Map<String, Object> progress, Map<String, Object> stats, List<Location> locations,
            List<String> triggered, List<?> decisions) {
        if (!RouteContinuationPolicy.isPending(progress)) {
            return RouteContinuationPolicy.response(progress, false);
        }
        List<ExplorationEvent> stories = eventMapper.selectList(new QueryWrapper<ExplorationEvent>()
                .eq("event_type", "story").eq("enabled", 1));
        boolean available = RouteContinuationPolicy.hasAvailableContinuation(progress,
                stories, locations, stats, parseJsonMap(player.getStoryFlagsJson()),
                player.getCurrentChapter(), triggered, decisions,
                event -> parseJsonMap(event.getRequiredConditionsJson()),
                location -> parseJsonMap(location.getUnlockConditionsJson()));
        return RouteContinuationPolicy.response(progress, available);
    }

    private Function<String, Map<String, Object>> ancestorConditionsLookup() {
        Map<String, Map<String, Object>> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key, missing -> {
            ExplorationEvent event = eventMapper.selectOne(new QueryWrapper<ExplorationEvent>()
                    .eq("event_key", missing).eq("enabled", 1));
            return event == null || !"story".equals(event.getEventType())
                    ? null : parseJsonMap(event.getRequiredConditionsJson());
        });
    }

    private boolean checkUnlockConditions(Map<String, Object> conds, Map<String, Object> playerStats,
                                          Player player, List<String> triggered) {
        return LocationUnlockPolicy.canAccessLocation(
                conds,
                playerStats,
                parseJsonMap(player.getStoryFlagsJson()),
                player.getCurrentChapter(),
                triggered);
    }
}
