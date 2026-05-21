package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
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

    public TradeServiceImpl(TradeListingMapper tradeListingMapper,
                            TradeRecordMapper tradeRecordMapper,
                            PlayerMapper playerMapper,
                            PlayerInventoryMapper playerInventoryMapper,
                            PlayerEquipmentMapper playerEquipmentMapper,
                            PlayerLogMapper playerLogMapper,
                            ItemMapper itemMapper,
                            EquipmentMapper equipmentMapper,
                            QuestService questService) {
        this.tradeListingMapper = tradeListingMapper;
        this.tradeRecordMapper = tradeRecordMapper;
        this.playerMapper = playerMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.playerLogMapper = playerLogMapper;
        this.itemMapper = itemMapper;
        this.equipmentMapper = equipmentMapper;
        this.questService = questService;
    }

    @Override
    @Transactional
    public TradeListingVO createItemListing(Long sellerPlayerId, String itemKey, int quantity, int unitPrice) {
        if (quantity <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "数量必须大于0");
        if (unitPrice <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "单价必须大于0");

        Item item = getItemDef(itemKey);
        if (item == null || item.getEnabled() == 0)
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "物品不存在或已禁用");

        PlayerInventory inv = getInventory(sellerPlayerId, itemKey);
        if (inv == null || inv.getQuantity() < quantity)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "物品数量不足");

        // Deduct from inventory (escrow)
        inv.setQuantity(inv.getQuantity() - quantity);
        playerInventoryMapper.updateById(inv);

        // Create listing
        TradeListing listing = new TradeListing();
        listing.setListingNo(generateListingNo());
        listing.setSellerPlayerId(sellerPlayerId);
        listing.setListingType("item");
        listing.setItemKey(itemKey);
        listing.setQuantity(quantity);
        listing.setUnitPrice(unitPrice);
        listing.setTotalPrice(unitPrice * quantity);
        listing.setStatus("active");
        listing.setCreatedAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        tradeListingMapper.insert(listing);

        writeLog(sellerPlayerId, "trade_list", "上架物品 " + item.getName() + " ×" + quantity + "，单价 " + unitPrice + " 金币");

        return toListingVO(listing, true);
    }

    @Override
    @Transactional
    public TradeListingVO createEquipmentListing(Long sellerPlayerId, String equipmentKey, int unitPrice) {
        if (unitPrice <= 0) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "单价必须大于0");

        Equipment equip = getEquipmentDef(equipmentKey);
        if (equip == null || equip.getEnabled() == 0)
            throw new BusinessException(ErrorCode.EQUIP_NOT_FOUND, "装备不存在或已禁用");

        PlayerEquipment pe = getPlayerEquipment(sellerPlayerId, equipmentKey);
        if (pe == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "你未拥有此装备");
        if (pe.getEquipped() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已穿戴的装备不能上架");
        if (pe.getDurability() <= 0)
            throw new BusinessException(ErrorCode.EQUIP_BROKEN, "损坏的装备不能上架");
        if (pe.getListed() != null && pe.getListed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "此装备已上架");

        pe.setListed(1);
        playerEquipmentMapper.updateById(pe);

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
        tradeListingMapper.insert(listing);

        writeLog(sellerPlayerId, "trade_list", "上架装备 " + equip.getName() + "，单价 " + unitPrice + " 金币");

        return toListingVO(listing, true);
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
    public TradeBuyResultVO buyListing(Long buyerPlayerId, String listingNo) {
        // Lock the listing row for update
        QueryWrapper<TradeListing> lockQw = new QueryWrapper<>();
        lockQw.eq("listing_no", listingNo).last("FOR UPDATE");
        TradeListing listing = tradeListingMapper.selectOne(lockQw);

        if (listing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "挂单不存在");
        if (!"active".equals(listing.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该挂单已售出或已取消");
        if (listing.getSellerPlayerId().equals(buyerPlayerId))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "不能购买自己的挂单");

        Player buyer = playerMapper.selectById(buyerPlayerId);
        if (buyer == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "买家不存在");
        if (buyer.getCoins() == null || buyer.getCoins() < listing.getTotalPrice())
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "金币不足");

        Player seller = playerMapper.selectById(listing.getSellerPlayerId());
        if (seller == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "卖家不存在");

        // Transfer coins: buyer → seller
        buyer.setCoins(buyer.getCoins() - listing.getTotalPrice());
        playerMapper.updateById(buyer);
        seller.setCoins((seller.getCoins() != null ? seller.getCoins() : 0) + listing.getTotalPrice());
        playerMapper.updateById(seller);

        String transferredName = "";
        // Transfer asset
        if ("item".equals(listing.getListingType())) {
            // Add item to buyer inventory
            PlayerInventory buyerInv = getInventory(buyerPlayerId, listing.getItemKey());
            if (buyerInv != null) {
                buyerInv.setQuantity(buyerInv.getQuantity() + listing.getQuantity());
                playerInventoryMapper.updateById(buyerInv);
            } else {
                PlayerInventory newInv = new PlayerInventory();
                newInv.setPlayerId(buyerPlayerId);
                newInv.setItemKey(listing.getItemKey());
                newInv.setQuantity(listing.getQuantity());
                playerInventoryMapper.insert(newInv);
            }
            Item itemDef = getItemDef(listing.getItemKey());
            transferredName = (itemDef != null ? itemDef.getName() : listing.getItemKey());
        } else if ("equipment".equals(listing.getListingType())) {
            // Transfer equipment ownership
            QueryWrapper<PlayerEquipment> peQw = new QueryWrapper<>();
            peQw.eq("player_id", listing.getSellerPlayerId())
                 .eq("equipment_key", listing.getEquipmentKey());
            PlayerEquipment pe = playerEquipmentMapper.selectOne(peQw);
            if (pe != null) {
                pe.setPlayerId(buyerPlayerId);
                pe.setListed(0);
                pe.setEquipped(0);
                playerEquipmentMapper.updateById(pe);
            }
            Equipment equipDef = getEquipmentDef(listing.getEquipmentKey());
            transferredName = (equipDef != null ? equipDef.getName() : listing.getEquipmentKey());
        }

        // Mark listing as sold
        listing.setStatus("sold");
        listing.setSoldAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        tradeListingMapper.updateById(listing);

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
        tradeRecordMapper.insert(record);

        writeLog(buyerPlayerId, "trade_buy", "从市场购买 " + transferredName + " ×" + listing.getQuantity() + "，花费 " + listing.getTotalPrice() + " 金币");
        writeLog(listing.getSellerPlayerId(), "trade_sold", "挂单售出: " + transferredName + " ×" + listing.getQuantity() + "，收入 " + listing.getTotalPrice() + " 金币");

        Map<String, Object> transferred = new LinkedHashMap<>();
        transferred.put("type", listing.getListingType());
        if ("item".equals(listing.getListingType())) {
            transferred.put("itemKey", listing.getItemKey());
            transferred.put("itemName", transferredName);
            transferred.put("quantity", listing.getQuantity());
        } else {
            transferred.put("equipmentKey", listing.getEquipmentKey());
            transferred.put("equipmentName", transferredName);
        }

        TradeBuyResultVO vo = new TradeBuyResultVO();
        vo.setListingNo(listing.getListingNo());
        vo.setListingType(listing.getListingType());
        vo.setBuyerPlayerId(buyerPlayerId);
        vo.setSellerPlayerId(listing.getSellerPlayerId());
        vo.setTotalPrice(listing.getTotalPrice());
        vo.setTransferredItem(transferred);
        vo.setNewBuyerCoins(buyer.getCoins());

        // 自动任务进度
        try {
            questService.addProgress(buyerPlayerId, "trade_buy_count", 1, "trade", listing.getListingNo());
            questService.addProgress(listing.getSellerPlayerId(), "trade_sell_count", 1, "trade", listing.getListingNo());
        } catch (Exception ignored) {}

        return vo;
    }

    @Override
    @Transactional
    public TradeListingVO cancelListing(Long sellerPlayerId, String listingNo) {
        QueryWrapper<TradeListing> qw = new QueryWrapper<>();
        qw.eq("listing_no", listingNo);
        TradeListing listing = tradeListingMapper.selectOne(qw);

        if (listing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "挂单不存在");
        if (!"active".equals(listing.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该挂单已失效");
        if (!listing.getSellerPlayerId().equals(sellerPlayerId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能取消自己的挂单");

        // Return assets
        String returnedName = "";
        if ("item".equals(listing.getListingType())) {
            PlayerInventory inv = getInventory(sellerPlayerId, listing.getItemKey());
            if (inv != null) {
                inv.setQuantity(inv.getQuantity() + listing.getQuantity());
                playerInventoryMapper.updateById(inv);
            } else {
                PlayerInventory newInv = new PlayerInventory();
                newInv.setPlayerId(sellerPlayerId);
                newInv.setItemKey(listing.getItemKey());
                newInv.setQuantity(listing.getQuantity());
                playerInventoryMapper.insert(newInv);
            }
            Item itemDef = getItemDef(listing.getItemKey());
            returnedName = (itemDef != null ? itemDef.getName() : listing.getItemKey());
        } else if ("equipment".equals(listing.getListingType())) {
            QueryWrapper<PlayerEquipment> peQw = new QueryWrapper<>();
            peQw.eq("player_id", sellerPlayerId)
                 .eq("equipment_key", listing.getEquipmentKey());
            PlayerEquipment pe = playerEquipmentMapper.selectOne(peQw);
            if (pe != null) {
                pe.setListed(0);
                playerEquipmentMapper.updateById(pe);
            }
            Equipment equipDef = getEquipmentDef(listing.getEquipmentKey());
            returnedName = (equipDef != null ? equipDef.getName() : listing.getEquipmentKey());
        }

        listing.setStatus("cancelled");
        listing.setCancelledAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        tradeListingMapper.updateById(listing);

        writeLog(sellerPlayerId, "trade_cancel", "取消挂单: " + returnedName);

        return toListingVO(listing, false);
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

    private PlayerEquipment getPlayerEquipment(Long playerId, String equipmentKey) {
        QueryWrapper<PlayerEquipment> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        return playerEquipmentMapper.selectOne(qw);
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
