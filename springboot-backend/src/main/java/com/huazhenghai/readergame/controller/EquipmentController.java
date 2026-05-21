package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.EquipItemRequest;
import com.huazhenghai.readergame.dto.RepairEquipmentRequest;
import com.huazhenghai.readergame.dto.UnequipItemRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.vo.EquipmentVO;
import com.huazhenghai.readergame.vo.PlayerEquipmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/equipment")
@Tag(name = "Equipment", description = "装备系统接口")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final PlayerMapper playerMapper;

    public EquipmentController(EquipmentService equipmentService, PlayerMapper playerMapper) {
        this.equipmentService = equipmentService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部装备定义")
    public Result<List<EquipmentVO>> getAllEquipment() {
        return Result.ok(equipmentService.getAllEquipment());
    }

    @GetMapping("/{playerId}")
    @Operation(summary = "获取玩家装备列表")
    public Result<List<PlayerEquipmentVO>> getPlayerEquipment(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(equipmentService.getPlayerEquipment(playerId, userId));
    }

    @GetMapping("/equipped/{playerId}")
    @Operation(summary = "获取当前穿戴装备(按slot分组)")
    public Result<Map<String, PlayerEquipmentVO>> getEquippedSlots(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        return Result.ok(equipmentService.getEquippedSlots(playerId, userId));
    }

    @GetMapping("/bonus/{playerId}")
    @Operation(summary = "获取装备属性加成")
    public Result<Map<String, Object>> getEquipmentBonus(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        // 校验玩家归属
        equipmentService.getPlayerEquipment(playerId, userId);
        return Result.ok(equipmentService.calculateEquipmentBonus(playerId));
    }

    @PostMapping("/equip")
    @Operation(summary = "穿戴装备")
    public Result<PlayerEquipmentVO> equip(@Valid @RequestBody EquipItemRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        PlayerEquipmentVO result = equipmentService.equip(req.getPlayerId(), req.getEquipmentKey(), userId);
        return Result.ok(result);
    }

    @PostMapping("/unequip")
    @Operation(summary = "卸下装备")
    public Result<PlayerEquipmentVO> unequip(@Valid @RequestBody UnequipItemRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        String eqKey = req.getEquipmentKey();
        if (eqKey == null && req.getSlot() != null) {
            eqKey = findEquippedKeyBySlot(req.getPlayerId(), req.getSlot());
        }
        PlayerEquipmentVO result = equipmentService.unequip(req.getPlayerId(), eqKey, userId);
        return Result.ok(result);
    }

    @PostMapping("/repair")
    @Operation(summary = "修理装备")
    public Result<Map<String, Object>> repair(@Valid @RequestBody RepairEquipmentRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        String eqKey = req.getEquipmentKey();
        if (eqKey == null && req.getSlot() != null) {
            eqKey = findEquippedKeyBySlot(req.getPlayerId(), req.getSlot());
        }
        Map<String, Object> result = equipmentService.repair(req.getPlayerId(), eqKey, userId);
        return Result.ok(result);
    }

    private String findEquippedKeyBySlot(Long playerId, String slot) {
        Long userId = LoginUserContext.get().getUserId();
        Map<String, PlayerEquipmentVO> slots = equipmentService.getEquippedSlots(playerId, userId);
        PlayerEquipmentVO eq = slots != null ? slots.get(slot) : null;
        return eq != null ? eq.getEquipmentKey() : slot;
    }

    @PostMapping("/repair-all")
    @Operation(summary = "修理全部装备")
    public Result<Map<String, Object>> repairAll(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        List<PlayerEquipmentVO> allEquipment = equipmentService.getPlayerEquipment(playerId, userId);
        int totalCost = 0;
        int repairedCount = 0;
        for (PlayerEquipmentVO eq : allEquipment) {
            if (eq.getDurability() < eq.getMaxDurability()) {
                try {
                    Map<String, Object> result = equipmentService.repair(playerId, eq.getEquipmentKey(), userId);
                    if (result.containsKey("cost")) {
                        totalCost += Integer.parseInt(result.get("cost").toString());
                    }
                    repairedCount++;
                } catch (Exception ignored) {}
            }
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("cost", totalCost);
        response.put("repaired", repairedCount);
        return Result.ok(response);
    }

    // ─── 装备套装 (占位) ───

    @GetMapping("/sets")
    @Operation(summary = "获取所有装备套装定义")
    public Result<List<Map<String, Object>>> getEquipmentSets() {
        return Result.ok(java.util.Collections.emptyList());
    }

    @GetMapping("/sets/my/{playerId}")
    @Operation(summary = "获取玩家已激活的套装加成")
    public Result<Map<String, Object>> getMySetBonuses(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("activeSets", java.util.Collections.emptyList());
        response.put("bonuses", java.util.Collections.emptyList());
        return Result.ok(response);
    }

    private void validatePlayerOwnership(Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }
}
