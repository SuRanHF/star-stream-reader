package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerInventory;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.SellItemResultVO;
import com.huazhenghai.readergame.vo.UseItemResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final PlayerMapper playerMapper;
    private final ItemMapper itemMapper;
    private final PlayerInventoryMapper inventoryMapper;
    private final PlayerLogService playerLogService;
    private final ObjectMapper objectMapper;

    public InventoryServiceImpl(PlayerMapper playerMapper,
                                ItemMapper itemMapper,
                                PlayerInventoryMapper inventoryMapper,
                                PlayerLogService playerLogService,
                                ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.playerLogService = playerLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<InventoryItemVO> getInventory(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的背包");

        QueryWrapper<PlayerInventory> query = new QueryWrapper<>();
        query.eq("player_id", playerId).gt("quantity", 0);
        List<PlayerInventory> rows = inventoryMapper.selectList(query);

        List<InventoryItemVO> result = new ArrayList<>();
        for (PlayerInventory inv : rows) {
            QueryWrapper<Item> itemQuery = new QueryWrapper<>();
            itemQuery.eq("item_key", inv.getItemKey()).eq("enabled", 1);
            Item item = itemMapper.selectOne(itemQuery);
            if (item == null) continue;

            InventoryItemVO vo = new InventoryItemVO();
            vo.setItemKey(item.getItemKey());
            vo.setName(item.getName());
            vo.setItemType(item.getItemType());
            vo.setRarity(item.getRarity());
            vo.setDescription(item.getDescription());
            vo.setQuantity(inv.getQuantity());
            vo.setEffects(parseJsonMap(item.getEffectsJson()));
            vo.setConsumeOnUse(item.getConsumeOnUse() != null && item.getConsumeOnUse() == 1);
            vo.setSellPrice(item.getSellPrice() != null ? item.getSellPrice() : 0);
            vo.setMaxStack(item.getMaxStack() != null ? item.getMaxStack() : 999);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public int addItem(Long playerId, String itemKey, int quantity) {
        if (quantity <= 0) return 0;

        QueryWrapper<Item> itemQuery = new QueryWrapper<>();
        itemQuery.eq("item_key", itemKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(itemQuery);
        if (item == null) return 0;

        int maxStack = item.getMaxStack() != null ? item.getMaxStack() : 999;

        QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
        invQuery.eq("player_id", playerId).eq("item_key", itemKey);
        PlayerInventory existing = inventoryMapper.selectOne(invQuery);

        if (existing != null) {
            int newQty = Math.min(existing.getQuantity() + quantity, maxStack);
            existing.setQuantity(newQty);
            inventoryMapper.updateById(existing);
            return newQty;
        } else {
            PlayerInventory inv = new PlayerInventory();
            inv.setPlayerId(playerId);
            inv.setItemKey(itemKey);
            inv.setQuantity(Math.min(quantity, maxStack));
            inventoryMapper.insert(inv);
            return inv.getQuantity();
        }
    }

    @Override
    @Transactional
    public boolean removeItem(Long playerId, String itemKey, int quantity) {
        if (quantity <= 0) return true;

        QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
        invQuery.eq("player_id", playerId).eq("item_key", itemKey);
        PlayerInventory existing = inventoryMapper.selectOne(invQuery);
        if (existing == null) return false;
        if (existing.getQuantity() < quantity) return false;

        int newQty = existing.getQuantity() - quantity;
        if (newQty <= 0) {
            inventoryMapper.deleteById(existing);
        } else {
            existing.setQuantity(newQty);
            inventoryMapper.updateById(existing);
        }
        return true;
    }

    @Override
    @Transactional
    public UseItemResultVO useItem(Long playerId, String itemKey, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        QueryWrapper<Item> itemQuery = new QueryWrapper<>();
        itemQuery.eq("item_key", itemKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(itemQuery);
        if (item == null)
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "物品不存在: " + itemKey);

        if (item.getConsumeOnUse() == null || item.getConsumeOnUse() != 1)
            throw new BusinessException(ErrorCode.ITEM_NOT_CONSUMABLE, "此物品不可使用: " + itemKey);

        // 检查背包中是否有此物品
        QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
        invQuery.eq("player_id", playerId).eq("item_key", itemKey);
        PlayerInventory inventory = inventoryMapper.selectOne(invQuery);
        if (inventory == null || inventory.getQuantity() < 1)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "背包中无此物品或数量不足: " + itemKey);

        // 解析效果
        Map<String, Object> effects = parseJsonMap(item.getEffectsJson());
        Map<String, Object> applied = new LinkedHashMap<>();

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());

        for (Map.Entry<String, Object> entry : effects.entrySet()) {
            String key = entry.getKey();
            int val = toInt(entry.getValue(), 0);

            switch (key) {
                case "restore_stamina": {
                    int current = toInt(stats.get("stamina"), 0);
                    int maxStamina = toInt(stats.get("maxStamina"), 100);
                    int added = Math.min(val, maxStamina - current);
                    stats.put("stamina", current + added);
                    applied.put("stamina", added);
                    break;
                }
                case "heal_hp": {
                    int current = toInt(stats.get("hp"), 0);
                    int maxHp = toInt(stats.get("maxHp"), 100);
                    int healed = Math.min(val, maxHp - current);
                    stats.put("hp", current + healed);
                    applied.put("hp", healed);
                    break;
                }
                case "story_fragments":
                    player.setStoryFragments(player.getStoryFragments() + val);
                    applied.put("storyFragments", val);
                    break;
                case "channel_heat": {
                    int current = toInt(stats.get("channelHeat"), 0);
                    stats.put("channelHeat", current + val);
                    applied.put("channelHeat", val);
                    break;
                }
                case "abyss_mark":
                    player.setAbyssMark(player.getAbyssMark() + val);
                    applied.put("abyssMark", val);
                    break;
                case "world_line_shift": {
                    int current = toInt(stats.get("worldLineShift"), 0);
                    stats.put("worldLineShift", current + val);
                    applied.put("worldLineShift", val);
                    break;
                }
                default:
                    break;
            }
        }

        // 消耗物品
        int newQty = inventory.getQuantity() - 1;
        if (newQty <= 0) {
            inventoryMapper.deleteById(inventory);
        } else {
            inventory.setQuantity(newQty);
            inventoryMapper.updateById(inventory);
        }

        // 保存玩家
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
        }
        playerMapper.updateById(player);

        // 日志
        playerLogService.addLog(playerId, "item_use", "使用了 " + item.getName());

        UseItemResultVO result = new UseItemResultVO();
        result.setItemKey(item.getItemKey());
        result.setItemName(item.getName());
        result.setRemainingQuantity(newQty);
        result.setAppliedEffects(applied);
        return result;
    }

    @Override
    @Transactional
    public SellItemResultVO sellItem(Long playerId, String itemKey, int quantity, Long userId) {
        if (quantity <= 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "出售数量必须大于0");

        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        QueryWrapper<Item> itemQuery = new QueryWrapper<>();
        itemQuery.eq("item_key", itemKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(itemQuery);
        if (item == null)
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "物品不存在: " + itemKey);

        int sellPrice = item.getSellPrice() != null ? item.getSellPrice() : 0;
        if (sellPrice <= 0)
            throw new BusinessException(ErrorCode.ITEM_CANNOT_SELL, "此物品不可出售: " + itemKey);

        QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
        invQuery.eq("player_id", playerId).eq("item_key", itemKey);
        PlayerInventory inventory = inventoryMapper.selectOne(invQuery);
        if (inventory == null || inventory.getQuantity() < quantity)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "背包中无此物品或数量不足: " + itemKey);

        int actualQty = Math.min(quantity, inventory.getQuantity());

        int newQty = inventory.getQuantity() - actualQty;
        if (newQty <= 0) {
            inventoryMapper.deleteById(inventory);
        } else {
            inventory.setQuantity(newQty);
            inventoryMapper.updateById(inventory);
        }

        int totalCoins = sellPrice * actualQty;
        player.setCoins(player.getCoins() + totalCoins);
        playerMapper.updateById(player);

        playerLogService.addLog(playerId, "item_sell",
                String.format("出售了 %d 个 %s，获得 %d 金币", actualQty, item.getName(), totalCoins));

        SellItemResultVO result = new SellItemResultVO();
        result.setItemKey(item.getItemKey());
        result.setItemName(item.getName());
        result.setSellQuantity(actualQty);
        result.setSellPrice(sellPrice);
        result.setTotalCoinsGained(totalCoins);
        result.setRemainingQuantity(newQty);
        return result;
    }

    // ─── 工具方法 ───

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
