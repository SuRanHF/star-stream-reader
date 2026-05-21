package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.MapUtils;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.SellItemRequest;
import com.huazhenghai.readergame.dto.UseItemRequest;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.SellItemResultVO;
import com.huazhenghai.readergame.vo.UseItemResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "物品背包系统接口")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{playerId}")
    @Operation(summary = "查看玩家背包")
    public Result<List<InventoryItemVO>> getInventory(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        List<InventoryItemVO> inventory = inventoryService.getInventory(playerId, userId);
        return Result.ok(inventory);
    }

    @PostMapping("/use")
    @Operation(summary = "使用物品")
    public Result<UseItemResultVO> useItem(@Valid @RequestBody UseItemRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        UseItemResultVO result = inventoryService.useItem(req.getPlayerId(), req.getItemKey(), userId);
        return Result.ok(result);
    }

    @PostMapping("/sell")
    @Operation(summary = "出售物品")
    public Result<SellItemResultVO> sellItem(@Valid @RequestBody SellItemRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        SellItemResultVO result = inventoryService.sellItem(
                req.getPlayerId(), req.getItemKey(), req.getQuantity(), userId);
        return Result.ok(result);
    }

    @PostMapping("/sell-batch")
    @Operation(summary = "批量出售物品")
    public Result<Map<String, Object>> sellBatch(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) body.get("items");
        if (items == null) throw new BusinessException(ErrorCode.MISSING_FIELDS, "缺少参数: items");
        int totalCoins = 0;
        java.util.List<String> soldItems = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            String itemKey = MapUtils.getStringRequired(item, "itemKey");
            int qty = MapUtils.toInt(item.get("quantity"), 1);
            SellItemResultVO result = inventoryService.sellItem(playerId, itemKey, qty, userId);
            if (result.getSellQuantity() > 0) {
                totalCoins += result.getTotalCoinsGained();
                soldItems.add(itemKey);
            }
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("coins", totalCoins);
        response.put("items", soldItems);
        return Result.ok(response);
    }

    @PostMapping("/use-batch")
    @Operation(summary = "批量使用物品")
    public Result<Map<String, Object>> useBatch(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        String itemKey = MapUtils.getStringRequired(body, "itemKey");
        int quantity = MapUtils.getInt(body, "quantity");
        int used = 0;
        for (int i = 0; i < quantity; i++) {
            try {
                UseItemResultVO result = inventoryService.useItem(playerId, itemKey, userId);
                used++;
            } catch (Exception e) { break; }
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("used", used);
        response.put("itemKey", itemKey);
        return Result.ok(response);
    }

    @GetMapping("/synthesis/recipes")
    @Operation(summary = "获取合成配方")
    public Result<Map<String, Object>> getSynthesisRecipes() {
        // Return empty recipes for now - synthesis not yet implemented
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("recipes", java.util.Collections.emptyList());
        return Result.ok(response);
    }

    @PostMapping("/synthesis")
    @Operation(summary = "合成物品")
    public Result<Map<String, Object>> synthesize(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        String recipeKey = MapUtils.getString(body, "recipeKey");
        // Synthesis not yet implemented - return error
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", false);
        response.put("recipe", recipeKey);
        response.put("error", "合成系统尚未实现");
        return Result.ok(response);
    }

    @PostMapping("/synthesis-all")
    @Operation(summary = "批量合成物品")
    public Result<Map<String, Object>> synthesizeAll(@RequestBody Map<String, Object> body) {
        Long userId = LoginUserContext.get().getUserId();
        Long playerId = MapUtils.getLong(body, "playerId");
        String recipeKey = MapUtils.getString(body, "recipeKey");
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("times", 0);
        response.put("recipe", recipeKey);
        return Result.ok(response);
    }
}
