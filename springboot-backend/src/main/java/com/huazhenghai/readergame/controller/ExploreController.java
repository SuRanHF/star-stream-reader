package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.ExploreRequest;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.ExploreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/explore")
@Tag(name = "Explore", description = "探索系统接口")
public class ExploreController {

    private final ExploreService exploreService;
    private final com.huazhenghai.readergame.mapper.LocationMapper locationMapper;

    public ExploreController(ExploreService exploreService,
                             com.huazhenghai.readergame.mapper.LocationMapper locationMapper) {
        this.exploreService = exploreService;
        this.locationMapper = locationMapper;
    }

    @GetMapping("/locations/{playerId}")
    @Operation(summary = "获取玩家可探索地点列表")
    public Result<Map<String, Object>> getLocations(@PathVariable Long playerId) {
        java.util.List<com.huazhenghai.readergame.entity.Location> locations =
                locationMapper.selectList(null);
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (com.huazhenghai.readergame.entity.Location loc : locations) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("location_key", loc.getLocationKey());
            m.put("location_name", loc.getName());
            m.put("description", loc.getDescription());
            m.put("danger_level", loc.getDangerLevel());
            m.put("unlock_conditions", loc.getUnlockConditionsJson());
            m.put("is_unlocked", true);
            list.add(m);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
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
}
