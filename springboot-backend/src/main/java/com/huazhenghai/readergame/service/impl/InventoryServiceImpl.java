package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Equipment;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerEquipment;
import com.huazhenghai.readergame.entity.PlayerInventory;
import com.huazhenghai.readergame.entity.SynthesisRecipe;
import com.huazhenghai.readergame.mapper.EquipmentMapper;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.mapper.PlayerEquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.SynthesisRecipeMapper;
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
    private final SynthesisRecipeMapper recipeMapper;
    private final PlayerLogService playerLogService;
    private final ObjectMapper objectMapper;
    private final PlayerEquipmentMapper playerEquipmentMapper;
    private final EquipmentMapper equipmentMapper;

    public InventoryServiceImpl(PlayerMapper playerMapper,
                                ItemMapper itemMapper,
                                PlayerInventoryMapper inventoryMapper,
                                SynthesisRecipeMapper recipeMapper,
                                PlayerLogService playerLogService,
                                ObjectMapper objectMapper,
                                PlayerEquipmentMapper playerEquipmentMapper,
                                EquipmentMapper equipmentMapper) {
        this.playerMapper = playerMapper;
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.recipeMapper = recipeMapper;
        this.playerLogService = playerLogService;
        this.objectMapper = objectMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.equipmentMapper = equipmentMapper;
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

        // 同时查询玩家拥有的装备，合并到背包展示中
        QueryWrapper<PlayerEquipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("player_id", playerId);
        List<PlayerEquipment> eqRows = playerEquipmentMapper.selectList(eqQuery);
        for (PlayerEquipment pe : eqRows) {
            Equipment eq = equipmentMapper.selectOne(
                    new QueryWrapper<Equipment>().eq("equipment_key", pe.getEquipmentKey()).eq("enabled", 1));
            if (eq == null) continue;

            InventoryItemVO vo = new InventoryItemVO();
            vo.setItemKey(eq.getEquipmentKey());
            vo.setName(eq.getName());
            vo.setItemType(eq.getSlot() != null ? eq.getSlot() : "equipment");
            vo.setRarity(eq.getRarity());
            vo.setDescription(eq.getDescription());
            vo.setQuantity(1);
            Map<String, Object> effects = new LinkedHashMap<>();
            effects.put("equipped", pe.getEquipped() != null && pe.getEquipped() == 1);
            effects.put("durability", pe.getDurability());
            effects.put("maxDurability", eq.getMaxDurability());
            effects.put("slot", eq.getSlot());
            vo.setEffects(effects);
            vo.setConsumeOnUse(false);
            vo.setSellPrice(eq.getSellPrice() != null ? eq.getSellPrice() : 0);
            vo.setMaxStack(1);
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

    // ─── 合成系统 ───

    @Override
    public List<Map<String, Object>> getRecipes() {
        QueryWrapper<SynthesisRecipe> query = new QueryWrapper<>();
        query.eq("enabled", 1);
        List<SynthesisRecipe> recipes = recipeMapper.selectList(query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SynthesisRecipe r : recipes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("recipeKey", r.getRecipeKey());
            map.put("name", r.getName());
            map.put("description", r.getDescription());
            map.put("resultItemKey", r.getResultItemKey());
            map.put("resultItemName", deriveItemName(r));
            map.put("resultQuantity", r.getResultQuantity());
            map.put("ingredients", parseJsonMap(r.getIngredientsJson()));
            map.put("costCoins", r.getCostCoins());
            // 查询合成结果物品的效果
            map.put("resultEffects", deriveResultEffects(r.getResultItemKey()));
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> synthesize(Long playerId, String recipeKey, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        QueryWrapper<SynthesisRecipe> recipeQuery = new QueryWrapper<>();
        recipeQuery.eq("recipe_key", recipeKey).eq("enabled", 1);
        SynthesisRecipe recipe = recipeMapper.selectOne(recipeQuery);
        if (recipe == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "合成配方不存在: " + recipeKey);

        // 解析所需材料
        Map<String, Object> ingredients = parseJsonMap(recipe.getIngredientsJson());
        if (ingredients.isEmpty())
            throw new BusinessException(ErrorCode.INVALID_FORMAT, "配方材料配置错误: " + recipeKey);

        // 检查材料是否足够
        for (Map.Entry<String, Object> entry : ingredients.entrySet()) {
            String requiredItemKey = entry.getKey();
            int requiredQty = toInt(entry.getValue(), 0);
            if (requiredQty <= 0) continue;

            QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
            invQuery.eq("player_id", playerId).eq("item_key", requiredItemKey);
            PlayerInventory inv = inventoryMapper.selectOne(invQuery);
            int owned = (inv != null) ? inv.getQuantity() : 0;
            if (owned < requiredQty) {
                QueryWrapper<Item> itemQuery = new QueryWrapper<>();
                itemQuery.eq("item_key", requiredItemKey);
                Item item = itemMapper.selectOne(itemQuery);
                String itemName = (item != null) ? item.getName() : requiredItemKey;
                throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH,
                        String.format("材料不足: %s，需要 %d 个，拥有 %d 个", itemName, requiredQty, owned));
            }
        }

        // 检查金币
        int costCoins = recipe.getCostCoins() != null ? recipe.getCostCoins() : 0;
        if (player.getCoins() < costCoins)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    String.format("金币不足: 需要 %d，拥有 %d", costCoins, player.getCoins()));

        // 扣除材料
        for (Map.Entry<String, Object> entry : ingredients.entrySet()) {
            String requiredItemKey = entry.getKey();
            int requiredQty = toInt(entry.getValue(), 0);
            if (requiredQty <= 0) continue;
            removeItem(playerId, requiredItemKey, requiredQty);
        }

        // 扣除金币
        if (costCoins > 0) {
            player.setCoins(player.getCoins() - costCoins);
        }

        playerMapper.updateById(player);

        // 添加合成结果
        int resultQty = recipe.getResultQuantity() != null ? recipe.getResultQuantity() : 1;
        addItem(playerId, recipe.getResultItemKey(), resultQty);

        playerLogService.addLog(playerId, "synthesis",
                String.format("合成了 %d 个 %s（配方: %s）", resultQty, recipe.getResultItemKey(), recipe.getName()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("recipe", recipeKey);
        response.put("resultItemKey", recipe.getResultItemKey());
        response.put("resultQuantity", resultQty);
        response.put("costCoins", costCoins);
        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> synthesizeAll(Long playerId, String recipeKey, int times, Long userId) {
        if (times <= 0) times = 1;
        if (times > 99) times = 99;
        int successCount = 0;
        String lastError = null;
        for (int i = 0; i < times; i++) {
            try {
                synthesize(playerId, recipeKey, userId);
                successCount++;
            } catch (BusinessException e) {
                lastError = e.getMessage();
                break;
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("times", successCount);
        response.put("recipe", recipeKey);
        if (successCount == 0 && lastError != null) {
            response.put("error", lastError);
        }
        return response;
    }

    /**
     * 从配方名提取结果物品名。配方名格式通常为"合成XXX"，去掉"合成"前缀即为物品名。
     */
    private String deriveItemName(SynthesisRecipe recipe) {
        if (recipe == null) return "";
        String name = recipe.getName();
        if (name == null || name.isEmpty()) return recipe.getResultItemKey();
        if (name.startsWith("合成")) return name.substring(2);
        return name;
    }

    /**
     * 查询合成结果物品的效果。
     * 先从 items 表查（effectJson），再从 equipment 表查（baseStats + specialEffects）。
     */
    private Map<String, Object> deriveResultEffects(String resultItemKey) {
        Map<String, Object> effects = new LinkedHashMap<>();
        if (resultItemKey == null || resultItemKey.isBlank()) return effects;
        // 查 items 表
        QueryWrapper<Item> itemQuery = new QueryWrapper<>();
        itemQuery.eq("item_key", resultItemKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(itemQuery);
        if (item != null) {
            Map<String, Object> itemEffects = parseJsonMap(item.getEffectsJson());
            if (itemEffects != null) effects.putAll(itemEffects);
        }
        // 查 equipment 表
        QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("equipment_key", resultItemKey).eq("enabled", 1);
        Equipment eq = equipmentMapper.selectOne(eqQuery);
        if (eq != null) {
            Map<String, Object> baseStats = parseJsonMap(eq.getBaseStatsJson());
            if (baseStats != null) effects.putAll(baseStats);
            Map<String, Object> specialEffects = parseJsonMap(eq.getSpecialEffectsJson());
            if (specialEffects != null) effects.putAll(specialEffects);
            effects.put("slot", eq.getSlot());
            effects.put("maxDurability", eq.getMaxDurability());
            effects.put("rarity", eq.getRarity());
        }
        return effects;
    }
}
