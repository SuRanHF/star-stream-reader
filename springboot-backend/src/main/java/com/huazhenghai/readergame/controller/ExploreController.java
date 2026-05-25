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
            // 真实解锁检查
            Map<String, Object> unlockConds = parseJsonMap(loc.getUnlockConditionsJson());
            boolean unlocked = unlockConds.isEmpty();
            if (!unlocked && unlockConds.containsKey("required_level")) {
                int required = unlockConds.get("required_level") instanceof Number
                        ? ((Number) unlockConds.get("required_level")).intValue() : 0;
                int level = playerStats.get("level") instanceof Number
                        ? ((Number) playerStats.get("level")).intValue() : 1;
                unlocked = level >= required;
            }
            m.put("is_unlocked", unlocked);

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
}
