package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.WorldlineSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/worldline")
@Tag(name = "Worldline", description = "世界线全局状态")
public class WorldlineController {

    private final WorldlineService worldlineService;

    public WorldlineController(WorldlineService worldlineService) {
        this.worldlineService = worldlineService;
    }

    @GetMapping("/states")
    @Operation(summary = "获取全部世界状态")
    public Result<List<Map<String, Object>>> getWorldStates() {
        return Result.ok(worldlineService.getWorldStates());
    }

    @GetMapping("/summary")
    @Operation(summary = "获取世界线摘要")
    public Result<WorldlineSummaryVO> getWorldlineSummary() {
        return Result.ok(worldlineService.getWorldlineSummary());
    }

    @GetMapping("/history")
    @Operation(summary = "获取世界线变更历史")
    public Result<List<Map<String, Object>>> getWorldlineHistory() {
        return Result.ok(java.util.Collections.emptyList());
    }
}
