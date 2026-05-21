package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.vo.OnlineSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/online")
@Tag(name = "Online", description = "在线玩家")
public class OnlineController {

    private final OnlinePlayerService onlinePlayerService;

    public OnlineController(OnlinePlayerService onlinePlayerService) {
        this.onlinePlayerService = onlinePlayerService;
    }

    @GetMapping("/summary")
    @Operation(summary = "获取在线人数摘要")
    public Result<OnlineSummaryVO> getOnlineSummary() {
        return Result.ok(onlinePlayerService.getOnlineSummary());
    }

    @GetMapping("/players")
    @Operation(summary = "获取在线玩家列表")
    public Result<List<Map<String, Object>>> getOnlinePlayers() {
        return Result.ok(onlinePlayerService.getOnlinePlayers());
    }
}
