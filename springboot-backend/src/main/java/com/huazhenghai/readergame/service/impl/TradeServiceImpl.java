package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.TradeService;
import com.huazhenghai.readergame.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TradeServiceImpl implements TradeService {

    private static final DateTimeFormatter TL_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TradeListingMapper tradeListingMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PlayerEquipmentMapper playerEquipmentMapper;
    private final PlayerLogMapper playerLogMapper;
    private final ItemMapper itemMapper;
    private final EquipmentMapper equipmentMapper;
    private final QuestService questService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final PlayerActionReceiptService actionReceiptService;

    public TradeServiceImpl(TradeListingMapper tradeListingMapper,
                            TradeRecordMapper tradeRecordMapper,
                            PlayerMapper playerMapper,
                            PlayerInventoryMapper playerInventoryMapper,
                            PlayerEquipmentMapper playerEquipmentMapper,
                            PlayerLogMapper playerLogMapper,
                            ItemMapper itemMapper,
                            EquipmentMapper equipmentMapper,
                            QuestService questService,
                            PlayerResourceTransactionService resourceTransactionService,
                            PlayerActionReceiptService actionReceiptService) {
        this.tradeListingMapper = tradeListingMapper;
        this.tradeRecordMapper = tradeRecordMapper;
        this.playerMapper = playerMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.playerLogMapper = playerLogMapper;
        this.itemMapper = itemMapper;
        this.equipmentMapper = equipmentMapper;
        this.questService = questService;
        this.resourceTransactionService = resourceTransactionService;
        this.actionReceiptService = actionReceiptService;
    }

    @Override
    @Transactional
    public TradeListingVO createItemListing(Long sellerPlayerId, String itemKey, int quantity,
                                            int unitPrice, String requestKey) {
        validateRequestKey(requestKey);
        if (quantity <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "数量必须大于0");
        if (unitPrice <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "单价必须大于0");

        if (playerMapper.selectByIdForUpdate(sellerPlayerId) == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "卖家不存在");
        Map<String, Object> requestPayload = actionPayload(
                "itemKey", itemKey, "quantity", quantity, "unitPrice", unitPrice);
        TradeListingVO replay = actionReceiptService.findReplay(sellerPlayerId,
                "trade.list.item", requestKey, requestPayload, TradeListingVO.class);
        if (replay != null) return replay;
        int totalPrice = calculateTotalPrice(unitPrice, quantity);

        Item item = getItemDef(itemKey);
        if (item == null || item.getEnabled() == null || item.getEnabled() != 1)
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "物品不存在或已禁用");

        PlayerInventory inv = getInventoryForUpdate(sellerPlayerId, itemKey);
        if (inv == null || inv.getQuantity() < quantity)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "物品数量不足");

        // Deduct from inventory (escrow)
        int inventoryBefore = inv.getQuantity();
        int inventoryAfter = inventoryBefore - quantity;
        if (inventoryAfter == 0) {
            if (playerInventoryMapper.deleteById(inv) <= 0)
                throw new IllegalStateException("上架托管物品失败: " + itemKey);
        } else {
            inv.setQuantity(inventoryAfter);
            if (playerInventoryMapper.updateById(inv) <= 0)
                throw new IllegalStateException("更新上架托管物品失败: " + itemKey);
        }

        // Create listing
        TradeListing listing = new TradeListing();
        listing.setListingNo(generateListingNo());
        listing.setSellerPlayerId(sellerPlayerId);
        listing.setListingType("item");
        listing.setItemKey(itemKey);
        listing.setQuantity(quantity);
        listing.setUnitPrice(unitPrice);
        listing.setTotalPrice(totalPrice);
        listing.setStatus("active");
        listing.setCreatedAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        if (tradeListingMapper.insert(listing) <= 0)
            throw new IllegalStateException("创建物品挂单失败");

        String transactionGroupId = "market-listing:" + listing.getListingNo();
        recordResourceChange(sellerPlayerId, "item", itemKey, inventoryBefore, inventoryAfter,
                "market_escrow", listing.getListingNo(), transactionGroupId,
                transactionGroupId + ":seller:item:" + itemKey,
                Map.of("listingNo", listing.getListingNo(), "quantity", quantity));

        writeLog(sellerPlayerId, "trade_list", "上架物品 " + item.getName() + " ×" + quantity + "，单价 " + unitPrice + " 星币");

        TradeListingVO result = toListingVO(listing, true);
        actionReceiptService.record(sellerPlayerId, "trade.list.item", requestKey,
                requestPayload, result);
        return result;
    }

    @Override
    @Transactional
    public TradeListingVO createEquipmentListing(Long sellerPlayerId, String equipmentKey,
                                                 int unitPrice, String requestKey) {
        validateRequestKey(requestKey);
        if (unitPrice <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "单价必须大于0");

        if (playerMapper.selectByIdForUpdate(sellerPlayerId) == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "卖家不存在");
        Map<String, Object> requestPayload = actionPayload(
                "equipmentKey", equipmentKey, "unitPrice", unitPrice);
        TradeListingVO replay = actionReceiptService.findReplay(sellerPlayerId,
                "trade.list.equipment", requestKey, requestPayload, TradeListingVO.class);
        if (replay != null) return replay;

        Equipment equip = getEquipmentDef(equipmentKey);
        if (equip == null || equip.getEnabled() == 0)
            throw new BusinessException(ErrorCode.EQUIP_NOT_FOUND, "装备不存在或已禁用");

        PlayerEquipment pe = getPlayerEquipmentForUpdate(sellerPlayerId, equipmentKey);
        if (pe == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "你未拥有此装备");
        if (pe.getEquipped() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已穿戴的装备不能上架");
        if (pe.getDurability() <= 0)
            throw new BusinessException(ErrorCode.EQUIP_BROKEN, "损坏的装备不能上架");
        if (pe.getListed() != null && pe.getListed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "此装备已上架");

        pe.setListed(1);
        if (playerEquipmentMapper.updateById(pe) <= 0)
            throw new IllegalStateException("锁定上架装备失败: " + equipmentKey);

        TradeListing listing = new TradeListing();
        listing.setListingNo(generateListingNo());
        listing.setSellerPlayerId(sellerPlayerId);
        listing.setListingType("equipment");
        listing.setEquipmentKey(equipmentKey);
        listing.setQuantity(1);
        listing.setUnitPrice(unitPrice);
        listing.setTotalPrice(unitPrice);
        listing.setStatus("active");
        listing.setCreatedAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        if (tradeListingMapper.insert(listing) <= 0)
            throw new IllegalStateException("创建装备挂单失败");

        writeLog(sellerPlayerId, "trade_list", "上架装备 " + equip.getName() + "，单价 " + unitPrice + " 星币");

        TradeListingVO result = toListingVO(listing, true);
        actionReceiptService.record(sellerPlayerId, "trade.list.equipment", requestKey,
                requestPayload, result);
        return result;
    }

    @Override
    public Map<String, Object> getMarketListings(String type, String keyword, int page, int size) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 50);

        QueryWrapper<TradeListing> qw = new QueryWrapper<>();
        qw.eq("status", "active");

        if (type != null && !type.isBlank()) {
            qw.eq("listing_type", type);
        }

        if (keyword != null && !keyword.isBlank()) {
            // Resolve keyword to item_key or equipment_key
            qw.and(w -> w.like("item_key", keyword).or().like("equipment_key", keyword));
        }

        qw.orderByDesc("created_at");

        long total = tradeListingMapper.selectCount(qw);
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + "," + size);
        List<TradeListing> listings = tradeListingMapper.selectList(qw);

        List<TradeListingVO> items = new ArrayList<>();
        for (TradeListing l : listings) {
            items.add(toListingVO(l, false));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("listings", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public List<TradeListingVO> getMyListings(Long playerId, String status) {
        QueryWrapper<TradeListing> qw = new QueryWrapper<>();
        qw.eq("seller_player_id", playerId);
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        qw.orderByDesc("created_at");
        List<TradeListing> listings = tradeListingMapper.selectList(qw);

        List<TradeListingVO> result = new ArrayList<>();
        for (TradeListing l : listings) {
            result.add(toListingVO(l, false));
        }
        return result;
    }

    @Override
    @Transactional
    public TradeBuyResultVO buyListing(Long buyerPlayerId, String listingNo, String requestKey) {
        validateRequestKey(requestKey);
        // Lock the listing row for update
        QueryWrapper<TradeListing> lockQw = new QueryWrapper<>();
        lockQw.eq("listing_no", listingNo).last("FOR UPDATE");
        TradeListing listing = tradeListingMapper.selectOne(lockQw);

        if (listing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "挂单不存在");
        if (Objects.equals(listing.getSellerPlayerId(), buyerPlayerId))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "不能购买自己的挂单");

        Player buyer;
        Player seller;
        if (buyerPlayerId < listing.getSellerPlayerId()) {
            buyer = playerMapper.selectByIdForUpdate(buyerPlayerId);
            seller = playerMapper.selectByIdForUpdate(listing.getSellerPlayerId());
        } else {
            seller = playerMapper.selectByIdForUpdate(listing.getSellerPlayerId());
            buyer = playerMapper.selectByIdForUpdate(buyerPlayerId);
        }
        if (buyer == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "买家不存在");
        if (seller == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "卖家不存在");

        Map<String, Object> requestPayload = actionPayload("listingNo", listingNo);
        TradeBuyResultVO replay = actionReceiptService.findReplay(buyerPlayerId,
                "trade.buy", requestKey, requestPayload, TradeBuyResultVO.class);
        if (replay != null) return replay;
        if (!"active".equals(listing.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该挂单已售出或已取消");

        int totalPrice = listing.getTotalPrice() == null ? 0 : listing.getTotalPrice();
        int quantity = listing.getQuantity() == null ? 0 : listing.getQuantity();
        if (totalPrice <= 0 || quantity <= 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "挂单价格或数量无效");

        Item itemDef = null;
        Equipment equipDef = null;
        PlayerInventory buyerInv = null;
        PlayerEquipment sellerEquipment = null;
        int assetBalanceBefore = 0;
        int assetBalanceAfter = 0;
        String assetKey;
        if ("item".equals(listing.getListingType())) {
            assetKey = listing.getItemKey();
            itemDef = getItemDef(assetKey);
            if (itemDef == null || itemDef.getEnabled() == null || itemDef.getEnabled() != 1)
                throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "挂单物品不存在或已禁用");
            buyerInv = getInventoryForUpdate(buyerPlayerId, assetKey);
            assetBalanceBefore = buyerInv == null || buyerInv.getQuantity() == null
                    ? 0 : buyerInv.getQuantity();
            int maxStack = itemDef.getMaxStack() == null ? 999 : itemDef.getMaxStack();
            long nextQuantity = (long) assetBalanceBefore + quantity;
            if (nextQuantity > maxStack)
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "买家背包堆叠空间不足");
            assetBalanceAfter = Math.toIntExact(nextQuantity);
        } else if ("equipment".equals(listing.getListingType())) {
            assetKey = listing.getEquipmentKey();
            equipDef = getEquipmentDef(assetKey);
            if (equipDef == null || equipDef.getEnabled() == null || equipDef.getEnabled() != 1)
                throw new BusinessException(ErrorCode.EQUIP_NOT_FOUND, "挂单装备不存在或已禁用");
            QueryWrapper<PlayerEquipment> equipmentQuery = new QueryWrapper<>();
            equipmentQuery.eq("player_id", listing.getSellerPlayerId())
                    .eq("equipment_key", assetKey).last("FOR UPDATE");
            sellerEquipment = playerEquipmentMapper.selectOne(equipmentQuery);
            if (sellerEquipment == null || !Integer.valueOf(1).equals(sellerEquipment.getListed()))
                throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "挂单装备已不可交易");
        } else {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "未知的挂单类型");
        }

        int buyerCoinsBefore = buyer.getCoins() == null ? 0 : buyer.getCoins();
        int sellerCoinsBefore = seller.getCoins() == null ? 0 : seller.getCoins();
        if (buyerCoinsBefore < totalPrice)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "星币不足");
        int buyerCoinsAfter = Math.subtractExact(buyerCoinsBefore, totalPrice);
        int sellerCoinsAfter = Math.addExact(sellerCoinsBefore, totalPrice);
        buyer.setCoins(buyerCoinsAfter);
        if (playerMapper.updateById(buyer) <= 0)
            throw new IllegalStateException("买家星币扣款失败");
        seller.setCoins(sellerCoinsAfter);
        if (playerMapper.updateById(seller) <= 0)
            throw new IllegalStateException("卖家星币入账失败");

        String transferredName;
        if ("item".equals(listing.getListingType())) {
            if (buyerInv != null) {
                buyerInv.setQuantity(assetBalanceAfter);
                if (playerInventoryMapper.updateById(buyerInv) <= 0)
                    throw new IllegalStateException("买家物品入包失败: " + assetKey);
            } else {
                PlayerInventory newInv = new PlayerInventory();
                newInv.setPlayerId(buyerPlayerId);
                newInv.setItemKey(assetKey);
                newInv.setQuantity(assetBalanceAfter);
                if (playerInventoryMapper.insert(newInv) <= 0)
                    throw new IllegalStateException("买家物品入包失败: " + assetKey);
            }
            transferredName = itemDef.getName() != null ? itemDef.getName() : assetKey;
        } else {
            sellerEquipment.setPlayerId(buyerPlayerId);
            sellerEquipment.setListed(0);
            sellerEquipment.setEquipped(0);
            if (playerEquipmentMapper.updateById(sellerEquipment) <= 0)
                throw new IllegalStateException("市场装备转移失败: " + assetKey);
            transferredName = equipDef.getName() != null ? equipDef.getName() : assetKey;
        }

        // Mark listing as sold
        listing.setStatus("sold");
        listing.setSoldAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        if (tradeListingMapper.updateById(listing) <= 0)
            throw new IllegalStateException("更新挂单成交状态失败");

        // Write trade record
        TradeRecord record = new TradeRecord();
        record.setListingNo(listing.getListingNo());
        record.setSellerPlayerId(listing.getSellerPlayerId());
        record.setBuyerPlayerId(buyerPlayerId);
        record.setListingType(listing.getListingType());
        record.setItemKey(listing.getItemKey());
        record.setEquipmentKey(listing.getEquipmentKey());
        record.setQuantity(listing.getQuantity());
        record.setTotalPrice(listing.getTotalPrice());
        record.setStatus("success");
        record.setCreatedAt(LocalDateTime.now());
        if (tradeRecordMapper.insert(record) <= 0)
            throw new IllegalStateException("写入市场成交记录失败");

        String transactionGroupId = "market:" + listing.getListingNo();
        Map<String, Object> resourceMetadata = new LinkedHashMap<>();
        resourceMetadata.put("listingNo", listing.getListingNo());
        resourceMetadata.put("listingType", listing.getListingType());
        resourceMetadata.put("sellerPlayerId", listing.getSellerPlayerId());
        resourceMetadata.put("buyerPlayerId", buyerPlayerId);
        resourceMetadata.put("quantity", quantity);
        resourceMetadata.put("totalPrice", totalPrice);
        recordResourceChange(buyerPlayerId, "currency", "coins", buyerCoinsBefore, buyerCoinsAfter,
                "market_trade", listing.getListingNo(), transactionGroupId,
                transactionGroupId + ":buyer:currency:coins", resourceMetadata);
        recordResourceChange(listing.getSellerPlayerId(), "currency", "coins",
                sellerCoinsBefore, sellerCoinsAfter, "market_trade", listing.getListingNo(),
                transactionGroupId, transactionGroupId + ":seller:currency:coins", resourceMetadata);
        if ("item".equals(listing.getListingType())) {
            recordResourceChange(buyerPlayerId, "item", assetKey, assetBalanceBefore, assetBalanceAfter,
                    "market_trade", listing.getListingNo(), transactionGroupId,
                    transactionGroupId + ":buyer:item:" + assetKey, resourceMetadata);
        } else {
            recordResourceChange(listing.getSellerPlayerId(), "equipment", assetKey, 1, 0,
                    "market_trade", listing.getListingNo(), transactionGroupId,
                    transactionGroupId + ":seller:equipment:" + assetKey, resourceMetadata);
            recordResourceChange(buyerPlayerId, "equipment", assetKey, 0, 1,
                    "market_trade", listing.getListingNo(), transactionGroupId,
                    transactionGroupId + ":buyer:equipment:" + assetKey, resourceMetadata);
        }

        writeLog(buyerPlayerId, "trade_buy", "从市场购买 " + transferredName + " ×" + listing.getQuantity() + "，花费 " + listing.getTotalPrice() + " 星币");
        writeLog(listing.getSellerPlayerId(), "trade_sold", "挂单售出: " + transferredName + " ×" + listing.getQuantity() + "，收入 " + listing.getTotalPrice() + " 星币");

        Map<String, Object> transferred = new LinkedHashMap<>();
        transferred.put("type", listing.getListingType());
        if ("item".equals(listing.getListingType())) {
            transferred.put("itemKey", listing.getItemKey());
            transferred.put("itemName", transferredName);
            transferred.put("quantity", quantity);
        } else {
            transferred.put("equipmentKey", listing.getEquipmentKey());
            transferred.put("equipmentName", transferredName);
        }

        TradeBuyResultVO vo = new TradeBuyResultVO();
        vo.setListingNo(listing.getListingNo());
        vo.setListingType(listing.getListingType());
        vo.setBuyerPlayerId(buyerPlayerId);
        vo.setSellerPlayerId(listing.getSellerPlayerId());
        vo.setTotalPrice(totalPrice);
        vo.setTransferredItem(transferred);
        vo.setNewBuyerCoins(buyerCoinsAfter);

        // 自动任务进度
        try {
            questService.addProgress(buyerPlayerId, "trade_buy_count", 1, "trade", listing.getListingNo());
            questService.addProgress(listing.getSellerPlayerId(), "trade_sell_count", 1, "trade", listing.getListingNo());
        } catch (Exception ignored) {}

        actionReceiptService.record(buyerPlayerId, "trade.buy", requestKey, requestPayload, vo);
        return vo;
    }

    @Override
    @Transactional
    public TradeListingVO cancelListing(Long sellerPlayerId, String listingNo, String requestKey) {
        validateRequestKey(requestKey);
        QueryWrapper<TradeListing> qw = new QueryWrapper<>();
        qw.eq("listing_no", listingNo).last("FOR UPDATE");
        TradeListing listing = tradeListingMapper.selectOne(qw);

        if (listing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "挂单不存在");
        if (!Objects.equals(listing.getSellerPlayerId(), sellerPlayerId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能取消自己的挂单");

        if (playerMapper.selectByIdForUpdate(sellerPlayerId) == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "卖家不存在");
        Map<String, Object> requestPayload = actionPayload("listingNo", listingNo);
        TradeListingVO replay = actionReceiptService.findReplay(sellerPlayerId,
                "trade.cancel", requestKey, requestPayload, TradeListingVO.class);
        if (replay != null) return replay;
        if (!"active".equals(listing.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该挂单已失效");

        // Return assets
        String returnedName = "";
        if ("item".equals(listing.getListingType())) {
            PlayerInventory inv = getInventoryForUpdate(sellerPlayerId, listing.getItemKey());
            int quantityBefore = inv == null || inv.getQuantity() == null ? 0 : inv.getQuantity();
            int listingQuantity = listing.getQuantity() == null ? 0 : listing.getQuantity();
            if (listingQuantity <= 0)
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "挂单物品数量无效");
            Item itemDef = getItemDef(listing.getItemKey());
            if (itemDef != null && itemDef.getMaxStack() != null
                    && (long) quantityBefore + listingQuantity > itemDef.getMaxStack()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                        "背包堆叠空间不足，请先腾出位置再取消挂单");
            }
            int quantityAfter = Math.addExact(quantityBefore, listingQuantity);
            if (inv != null) {
                inv.setQuantity(quantityAfter);
                if (playerInventoryMapper.updateById(inv) <= 0)
                    throw new IllegalStateException("返还挂单物品失败: " + listing.getItemKey());
            } else {
                PlayerInventory newInv = new PlayerInventory();
                newInv.setPlayerId(sellerPlayerId);
                newInv.setItemKey(listing.getItemKey());
                newInv.setQuantity(quantityAfter);
                if (playerInventoryMapper.insert(newInv) <= 0)
                    throw new IllegalStateException("返还挂单物品失败: " + listing.getItemKey());
            }
            returnedName = (itemDef != null ? itemDef.getName() : listing.getItemKey());

            String transactionGroupId = "market-cancel:" + listing.getListingNo();
            recordResourceChange(sellerPlayerId, "item", listing.getItemKey(),
                    quantityBefore, quantityAfter, "market_cancel", listing.getListingNo(),
                    transactionGroupId, transactionGroupId + ":seller:item:" + listing.getItemKey(),
                    Map.of("listingNo", listing.getListingNo(), "quantity", listingQuantity));
        } else if ("equipment".equals(listing.getListingType())) {
            QueryWrapper<PlayerEquipment> peQw = new QueryWrapper<>();
            peQw.eq("player_id", sellerPlayerId)
                 .eq("equipment_key", listing.getEquipmentKey()).last("FOR UPDATE");
            PlayerEquipment pe = playerEquipmentMapper.selectOne(peQw);
            if (pe == null || !Integer.valueOf(1).equals(pe.getListed()))
                throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "待取消的挂单装备已不可用");
            pe.setListed(0);
            if (playerEquipmentMapper.updateById(pe) <= 0)
                throw new IllegalStateException("解除装备挂单状态失败: " + listing.getEquipmentKey());
            Equipment equipDef = getEquipmentDef(listing.getEquipmentKey());
            returnedName = (equipDef != null ? equipDef.getName() : listing.getEquipmentKey());
        } else {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "未知的挂单类型");
        }

        listing.setStatus("cancelled");
        listing.setCancelledAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        if (tradeListingMapper.updateById(listing) <= 0)
            throw new IllegalStateException("更新挂单取消状态失败");

        writeLog(sellerPlayerId, "trade_cancel", "取消挂单: " + returnedName);

        TradeListingVO result = toListingVO(listing, false);
        actionReceiptService.record(sellerPlayerId, "trade.cancel", requestKey,
                requestPayload, result);
        return result;
    }

    @Override
    public List<TradeRecordVO> getTradeRecords(Long playerId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 50);
        int offset = (page - 1) * size;

        QueryWrapper<TradeRecord> qw = new QueryWrapper<>();
        qw.and(w -> w.eq("seller_player_id", playerId).or().eq("buyer_player_id", playerId))
          .orderByDesc("created_at")
          .last("LIMIT " + offset + "," + size);
        List<TradeRecord> records = tradeRecordMapper.selectList(qw);

        List<TradeRecordVO> result = new ArrayList<>();
        for (TradeRecord r : records) {
            result.add(toRecordVO(r));
        }
        return result;
    }

    @Override
    public TradeSummaryVO getTradeSummary(Long playerId) {
        TradeSummaryVO vo = new TradeSummaryVO();

        QueryWrapper<TradeListing> activeQ = new QueryWrapper<>();
        activeQ.eq("seller_player_id", playerId).eq("status", "active");
        vo.setActiveListingCount(tradeListingMapper.selectCount(activeQ).intValue());

        QueryWrapper<TradeListing> soldQ = new QueryWrapper<>();
        soldQ.eq("seller_player_id", playerId).eq("status", "sold");
        vo.setSoldCount(tradeListingMapper.selectCount(soldQ).intValue());

        QueryWrapper<TradeRecord> boughtQ = new QueryWrapper<>();
        boughtQ.eq("buyer_player_id", playerId);
        vo.setBoughtCount(tradeRecordMapper.selectCount(boughtQ).intValue());

        QueryWrapper<TradeRecord> recentQ = new QueryWrapper<>();
        recentQ.and(w -> w.eq("seller_player_id", playerId).or().eq("buyer_player_id", playerId))
               .orderByDesc("created_at")
               .last("LIMIT 5");
        vo.setRecentTradeCount(tradeRecordMapper.selectCount(recentQ).intValue());

        return vo;
    }

    // ─── helpers ───

    private void validateRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank() || requestKey.trim().length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 必须为 1 到 64 个字符");
    }

    private Map<String, Object> actionPayload(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("操作参数必须为键值对");
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            payload.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return payload;
    }

    private String generateListingNo() {
        String base = "TL" + LocalDateTime.now().format(TL_DATE_FMT);
        String suffix = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return base + suffix;
    }

    private PlayerInventory getInventory(Long playerId, String itemKey) {
        QueryWrapper<PlayerInventory> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("item_key", itemKey);
        return playerInventoryMapper.selectOne(qw);
    }

    private PlayerInventory getInventoryForUpdate(Long playerId, String itemKey) {
        QueryWrapper<PlayerInventory> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("item_key", itemKey).last("FOR UPDATE");
        return playerInventoryMapper.selectOne(qw);
    }

    private PlayerEquipment getPlayerEquipment(Long playerId, String equipmentKey) {
        QueryWrapper<PlayerEquipment> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        return playerEquipmentMapper.selectOne(qw);
    }

    private PlayerEquipment getPlayerEquipmentForUpdate(Long playerId, String equipmentKey) {
        QueryWrapper<PlayerEquipment> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("equipment_key", equipmentKey).last("FOR UPDATE");
        return playerEquipmentMapper.selectOne(qw);
    }

    private int calculateTotalPrice(int unitPrice, int quantity) {
        try {
            return Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "挂单总价超出可用范围");
        }
    }

    private void recordResourceChange(Long playerId, String resourceType, String resourceKey,
                                      long balanceBefore, long balanceAfter, String sourceType,
                                      String sourceId, String transactionGroupId,
                                      String idempotencyKey, Map<String, Object> metadata) {
        long amount = Math.subtractExact(balanceAfter, balanceBefore);
        if (amount == 0) return;
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, balanceBefore, balanceAfter,
                sourceType, sourceId, transactionGroupId, idempotencyKey, metadata));
    }

    private Item getItemDef(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) return null;
        QueryWrapper<Item> qw = new QueryWrapper<>();
        qw.eq("item_key", itemKey);
        return itemMapper.selectOne(qw);
    }

    private Equipment getEquipmentDef(String equipmentKey) {
        if (equipmentKey == null || equipmentKey.isBlank()) return null;
        QueryWrapper<Equipment> qw = new QueryWrapper<>();
        qw.eq("equipment_key", equipmentKey);
        return equipmentMapper.selectOne(qw);
    }

    private TradeListingVO toListingVO(TradeListing l, boolean includeSellerName) {
        TradeListingVO vo = new TradeListingVO();
        vo.setId(l.getId());
        vo.setListingNo(l.getListingNo());
        vo.setSellerPlayerId(l.getSellerPlayerId());
        vo.setListingType(l.getListingType());
        vo.setItemKey(l.getItemKey());
        vo.setEquipmentKey(l.getEquipmentKey());
        vo.setQuantity(l.getQuantity());
        vo.setUnitPrice(l.getUnitPrice());
        vo.setTotalPrice(l.getTotalPrice());
        vo.setStatus(l.getStatus());
        vo.setCreatedAt(l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
        vo.setSoldAt(l.getSoldAt() != null ? l.getSoldAt().toString() : null);
        vo.setCancelledAt(l.getCancelledAt() != null ? l.getCancelledAt().toString() : null);

        if (includeSellerName || true) {
            Player seller = playerMapper.selectById(l.getSellerPlayerId());
            vo.setSellerName(seller != null ? seller.getPlayerName() : "未知");
        }

        if (l.getItemKey() != null) {
            Item itemDef = getItemDef(l.getItemKey());
            vo.setItemName(itemDef != null ? itemDef.getName() : l.getItemKey());
        }
        if (l.getEquipmentKey() != null) {
            Equipment equipDef = getEquipmentDef(l.getEquipmentKey());
            vo.setEquipmentName(equipDef != null ? equipDef.getName() : l.getEquipmentKey());
        }
        return vo;
    }

    private TradeRecordVO toRecordVO(TradeRecord r) {
        TradeRecordVO vo = new TradeRecordVO();
        vo.setId(r.getId());
        vo.setListingNo(r.getListingNo());
        vo.setSellerPlayerId(r.getSellerPlayerId());
        vo.setBuyerPlayerId(r.getBuyerPlayerId());
        vo.setListingType(r.getListingType());
        vo.setItemKey(r.getItemKey());
        vo.setEquipmentKey(r.getEquipmentKey());
        vo.setQuantity(r.getQuantity());
        vo.setTotalPrice(r.getTotalPrice());
        vo.setStatus(r.getStatus());
        vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);

        Player seller = playerMapper.selectById(r.getSellerPlayerId());
        vo.setSellerName(seller != null ? seller.getPlayerName() : "未知");
        Player buyer = playerMapper.selectById(r.getBuyerPlayerId());
        vo.setBuyerName(buyer != null ? buyer.getPlayerName() : "未知");

        if (r.getItemKey() != null) {
            Item itemDef = getItemDef(r.getItemKey());
            vo.setItemName(itemDef != null ? itemDef.getName() : r.getItemKey());
        }
        if (r.getEquipmentKey() != null) {
            Equipment equipDef = getEquipmentDef(r.getEquipmentKey());
            vo.setEquipmentName(equipDef != null ? equipDef.getName() : r.getEquipmentKey());
        }
        return vo;
    }

    private void writeLog(Long playerId, String type, String message) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        playerLogMapper.insert(log);
    }
}
