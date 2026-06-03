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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/explore")
@Tag(name = "Explore", description = "探索系统接口")
public class ExploreController {

    private final ExploreService exploreService;
    private final LocationMapper locationMapper;
    private final ExplorationEventMapper eventMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public ExploreController(ExploreService exploreService,
                             LocationMapper locationMapper,
                             ExplorationEventMapper eventMapper,
                             PlayerMapper playerMapper,
                             ObjectMapper objectMapper) {
        this.exploreService = exploreService;
        this.locationMapper = locationMapper;
        this.eventMapper = eventMapper;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/locations/{playerId}")
    @Operation(summary = "获取玩家可探索地点列表 (含故事耗尽状态)")
    public Result<Map<String, Object>> getLocations(@PathVariable Long playerId) {
        Player player = playerMapper.selectById(playerId);
        List<Location> locations = locationMapper.selectList(null);

        // 获取玩家已触发的故事事件列表
        final List<String> triggered = getTriggeredStoryEvents(player);
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
                    .allMatch(e -> triggered.contains(e.getEventKey()));
            m.put("stories_exhausted", exhausted);
            m.put("total_stories", storyEvents.size());
            long triggeredCount = storyEvents.stream()
                    .filter(e -> triggered.contains(e.getEventKey())).count();
            m.put("triggered_stories", (int) triggeredCount);

            list.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", list);
        return Result.ok(result);
    }

    @PostMapping("/start")
    @Operation(summary = "开始探索")
    public Result<Map<String, Object>> startExplore(@Valid @RequestBody ExploreRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> result = exploreService.startExplore(
                req.getPlayerId(), req.getLocationKey(), userId);
        return Result.ok(result);
    }

    @PostMapping("/choose")
    @Operation(summary = "故事事件中做出选择")
    public Result<Map<String, Object>> makeChoice(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = body.get("playerId") instanceof Number
                ? ((Number) body.get("playerId")).longValue() : null;
        String eventKey = (String) body.get("eventKey");
        int choiceIndex = body.get("choiceIndex") instanceof Number
                ? ((Number) body.get("choiceIndex")).intValue() : -1;

        if (playerId == null || eventKey == null || choiceIndex < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不完整");
        }
        Map<String, Object> result = exploreService.makeChoice(playerId, eventKey, choiceIndex, userId);
        return Result.ok(result);
    }

    @GetMapping("/map-nodes/{playerId}")
    @Operation(summary = "获取场景地图节点数据 (替代前端 mockData)")
    public Result<Map<String, Object>> getMapNodes(@PathVariable Long playerId) {
        Player player = playerMapper.selectById(playerId);
        List<Location> locations = locationMapper.selectList(null);
        final List<String> triggered = getTriggeredStoryEvents(player);
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
                    .allMatch(e -> triggered.contains(e.getEventKey()));
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
        List<Map<String, Object>> storyLog = exploreService.getStoryLog(playerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("story_log", storyLog);
        return Result.ok(result);
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

    @SuppressWarnings("unchecked")
    private boolean checkUnlockConditions(Map<String, Object> conds, Map<String, Object> playerStats,
                                          Player player, List<String> triggered) {
        if (conds.isEmpty()) return true;

        // 1. 等级要求
        if (conds.containsKey("required_level")) {
            int required = conds.get("required_level") instanceof Number
                    ? ((Number) conds.get("required_level")).intValue() : 0;
            int level = playerStats.get("level") instanceof Number
                    ? ((Number) playerStats.get("level")).intValue() : 1;
            if (level < required) return false;
        }

        // 2. 前置事件要求
        if (conds.containsKey("prerequisite_events")) {
            List<String> required = (List<String>) conds.get("prerequisite_events");
            for (String re : required) {
                if (!triggered.contains(re)) return false;
            }
        }

        // 3. 道具要求
        if (conds.containsKey("required_items")) {
            List<String> requiredItems = (List<String>) conds.get("required_items");
            Map<String, Object> flags = parseJsonMap(player.getStoryFlagsJson());
            List<String> held = (List<String>) flags.getOrDefault("held_items", Collections.emptyList());
            for (String item : requiredItems) {
                if (!held.contains(item)) return false;
            }
        }

        // 4. 章节要求
        if (conds.containsKey("chapter_min")) {
            int minChapter = conds.get("chapter_min") instanceof Number
                    ? ((Number) conds.get("chapter_min")).intValue() : 0;
            String curChapter = player.getCurrentChapter();
            if (curChapter == null) return minChapter <= 1;
            int curNum = 0;
            try { curNum = Integer.parseInt(curChapter.replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) {}
            if (curNum < minChapter) return false;
        }

        // 5. 选项解锁过的地点 (从 storyFlags 读取)
        if (conds.containsKey("unlock_by_choice")) {
            String locKey = (String) conds.get("unlock_by_choice");
            Map<String, Object> flags = parseJsonMap(player.getStoryFlagsJson());
            List<String> unlocked = (List<String>) flags.getOrDefault("unlocked_locations", Collections.emptyList());
            if (!unlocked.contains(locKey)) return false;
        }

        return true;
    }
}
