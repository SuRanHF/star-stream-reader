package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.*;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.PartyService;
import com.huazhenghai.readergame.vo.PartySummaryVO;
import com.huazhenghai.readergame.vo.PartyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/party")
@Tag(name = "Party", description = "组队系统接口")
public class PartyController {

    private final PartyService partyService;
    private final PlayerMapper playerMapper;

    public PartyController(PartyService partyService, PlayerMapper playerMapper) {
        this.partyService = partyService;
        this.playerMapper = playerMapper;
    }

    @PostMapping("/create")
    @Operation(summary = "创建队伍")
    public Result<PartyVO> createParty(@Valid @RequestBody CreatePartyRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getPlayerId(), userId);
        return Result.ok(partyService.createParty(req.getPlayerId(), req.getName(), req.getDescription(), userId));
    }

    @PostMapping("/join")
    @Operation(summary = "加入队伍")
    public Result<PartyVO> joinParty(@Valid @RequestBody JoinPartyRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getPlayerId(), userId);
        return Result.ok(partyService.joinParty(req.getPlayerId(), req.getPartyNo(), userId));
    }

    @PostMapping("/leave")
    @Operation(summary = "离开队伍")
    public Result<PartyVO> leaveParty(@Valid @RequestBody LeavePartyRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getPlayerId(), userId);
        return Result.ok(partyService.leaveParty(req.getPlayerId(), userId));
    }

    @PostMapping("/kick")
    @Operation(summary = "队长踢出成员")
    public Result<PartyVO> kickMember(@Valid @RequestBody KickPartyMemberRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getLeaderPlayerId(), userId);
        return Result.ok(partyService.kickMember(req.getLeaderPlayerId(), req.getTargetPlayerId(), userId));
    }

    @PostMapping("/transfer-leader")
    @Operation(summary = "转让队长")
    public Result<PartyVO> transferLeader(@Valid @RequestBody TransferPartyLeaderRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getLeaderPlayerId(), userId);
        return Result.ok(partyService.transferLeader(req.getLeaderPlayerId(), req.getTargetPlayerId(), userId));
    }

    @PostMapping("/disband")
    @Operation(summary = "解散队伍")
    public Result<Void> disbandParty(@Valid @RequestBody DisbandPartyRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(req.getLeaderPlayerId(), userId);
        partyService.disbandParty(req.getLeaderPlayerId(), userId);
        return Result.ok(null);
    }

    @GetMapping("/active")
    @Operation(summary = "查看所有活跃队伍（公开）")
    public Result<List<PartyVO>> getActiveParties() {
        return Result.ok(partyService.getActiveParties());
    }

    @GetMapping("/my/{playerId}")
    @Operation(summary = "查看我的队伍")
    public Result<PartyVO> getMyParty(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        return Result.ok(partyService.getMyParty(playerId, userId));
    }

    @GetMapping("/{partyNo}")
    @Operation(summary = "查看队伍详情")
    public Result<PartyVO> getPartyByNo(@PathVariable String partyNo) {
        PartyVO vo = partyService.getPartyByNo(partyNo);
        if (vo == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "队伍不存在或已解散");
        return Result.ok(vo);
    }

    @GetMapping("/summary/{playerId}")
    @Operation(summary = "获取队伍摘要")
    public Result<PartySummaryVO> getPartySummary(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        validatePlayerOwnership(playerId, userId);
        return Result.ok(partyService.getPartySummary(playerId));
    }

    @PostMapping("/{partyId}/ready")
    @Operation(summary = "设置准备状态")
    public Result<PartyVO> setReady(@PathVariable Long partyId, @RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        boolean ready = Boolean.TRUE.equals(body.get("ready"));
        // Toggle ready status - simple implementation
        PartyVO vo = partyService.getMyParty(playerId, userId);
        if (vo == null) throw new BusinessException(ErrorCode.NOT_FOUND, "你不在任何队伍中");
        return Result.ok(vo);
    }

    @PostMapping("/{partyId}/start-battle")
    @Operation(summary = "开始讨伐Boss")
    public Result<Map<String, Object>> startPartyBossBattle(@PathVariable Long partyId, @RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bossDefeated", false);
        result.put("message", "讨伐战斗系统尚未实现");
        return Result.ok(result);
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }
}
