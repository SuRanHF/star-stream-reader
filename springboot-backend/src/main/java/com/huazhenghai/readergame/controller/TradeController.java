package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.*;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.TradeService;
import com.huazhenghai.readergame.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@Tag(name = "Trade", description = "交易市场")
public class TradeController {

    private final TradeService tradeService;
    private final PlayerMapper playerMapper;

    public TradeController(TradeService tradeService, PlayerMapper playerMapper) {
        this.tradeService = tradeService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/listings")
    @Operation(summary = "获取市场挂单列表")
    public Result<Map<String, Object>> getMarketListings(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(tradeService.getMarketListings(type, keyword, page, size));
    }

    @PostMapping("/list/item")
    @Operation(summary = "上架物品")
    public Result<TradeListingVO> createItemListing(@Valid @RequestBody CreateItemListingRequest req) {
        validatePlayerOwnership(req.getSellerPlayerId());
        return Result.ok(tradeService.createItemListing(
                req.getSellerPlayerId(), req.getItemKey(), req.getQuantity(), req.getUnitPrice()));
    }

    @PostMapping("/list/equipment")
    @Operation(summary = "上架装备")
    public Result<TradeListingVO> createEquipmentListing(@Valid @RequestBody CreateEquipmentListingRequest req) {
        validatePlayerOwnership(req.getSellerPlayerId());
        return Result.ok(tradeService.createEquipmentListing(
                req.getSellerPlayerId(), req.getEquipmentKey(), req.getUnitPrice()));
    }

    @PostMapping("/buy")
    @Operation(summary = "购买挂单")
    public Result<TradeBuyResultVO> buyListing(@Valid @RequestBody BuyTradeListingRequest req) {
        validatePlayerOwnership(req.getBuyerPlayerId());
        return Result.ok(tradeService.buyListing(req.getBuyerPlayerId(), req.getListingNo()));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消挂单")
    public Result<TradeListingVO> cancelListing(@Valid @RequestBody CancelTradeListingRequest req) {
        validatePlayerOwnership(req.getSellerPlayerId());
        return Result.ok(tradeService.cancelListing(req.getSellerPlayerId(), req.getListingNo()));
    }

    @GetMapping("/my/{playerId}")
    @Operation(summary = "获取我的挂单")
    public Result<List<TradeListingVO>> getMyListings(
            @PathVariable Long playerId,
            @RequestParam(required = false) String status) {
        validatePlayerOwnership(playerId);
        return Result.ok(tradeService.getMyListings(playerId, status));
    }

    @GetMapping("/records/{playerId}")
    @Operation(summary = "获取交易记录")
    public Result<List<TradeRecordVO>> getTradeRecords(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePlayerOwnership(playerId);
        return Result.ok(tradeService.getTradeRecords(playerId, page, size));
    }

    @GetMapping("/summary/{playerId}")
    @Operation(summary = "获取交易摘要")
    public Result<TradeSummaryVO> getTradeSummary(@PathVariable Long playerId) {
        validatePlayerOwnership(playerId);
        return Result.ok(tradeService.getTradeSummary(playerId));
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
