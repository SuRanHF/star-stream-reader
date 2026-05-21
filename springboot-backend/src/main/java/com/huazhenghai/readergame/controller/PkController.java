package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.CreateChallengeRequest;
import com.huazhenghai.readergame.dto.ResolveChallengeRequest;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.PkService;
import com.huazhenghai.readergame.vo.PkRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "PK系统", description = "玩家对战、挑战管理、PK记录")
@RestController
@RequestMapping("/api/pk")
public class PkController {

    private final PkService pkService;

    public PkController(PkService pkService) {
        this.pkService = pkService;
    }

    @Operation(summary = "获取可挑战对手列表")
    @GetMapping("/opponents/{playerId}")
    public Result<List<Map<String, Object>>> getOpponents(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(pkService.getOpponents(playerId, userId));
    }

    @Operation(summary = "发起PK挑战")
    @PostMapping("/challenge")
    public Result<Map<String, Object>> createChallenge(@Valid @RequestBody CreateChallengeRequest request) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> result = pkService.createChallenge(
                request.getAttackerId(), request.getDefenderId(), request.getMode(), userId);
        return Result.ok(result);
    }

    @Operation(summary = "响应PK挑战（接受/拒绝）")
    @PostMapping("/challenge/resolve")
    public Result<Map<String, Object>> resolveChallenge(@Valid @RequestBody ResolveChallengeRequest request) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, Object> result = pkService.resolveChallenge(
                request.getChallengeId(), request.isAccept(), request.getPlayerId(), userId);
        return Result.ok(result);
    }

    @Operation(summary = "获取玩家PK记录")
    @GetMapping("/records/{playerId}")
    public Result<List<PkRecordVO>> getPKRecords(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(pkService.getPKRecords(playerId, userId));
    }
}
