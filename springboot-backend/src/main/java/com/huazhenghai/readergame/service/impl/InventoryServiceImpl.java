package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.dto.SellItemCommand;
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
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerStaminaProjectionService;
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
    private final EquipmentService equipmentService;
    private final PlayerEffectService playerEffectService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final PlayerActionReceiptService actionReceiptService;
    private final PlayerStaminaProjectionService staminaProjectionService;

    public InventoryServiceImpl(PlayerMapper playerMapper,
                                ItemMapper itemMapper,
                                PlayerInventoryMapper inventoryMapper,
                                SynthesisRecipeMapper recipeMapper,
                                PlayerLogService playerLogService,
                                ObjectMapper objectMapper,
                                PlayerEquipmentMapper playerEquipmentMapper,
                                EquipmentMapper equipmentMapper,
                                EquipmentService equipmentService,
                                PlayerEffectService playerEffectService,
                                PlayerResourceTransactionService resourceTransactionService,
                                PlayerActionReceiptService actionReceiptService,
                                PlayerStaminaProjectionService staminaProjectionService) {
        this.playerMapper = playerMapper;
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.recipeMapper = recipeMapper;
        this.playerLogService = playerLogService;
        this.objectMapper = objectMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.equipmentMapper = equipmentMapper;
        this.equipmentService = equipmentService;
        this.playerEffectService = playerEffectService;
        this.resourceTransactionService = resourceTransactionService;
        this.actionReceiptService = actionReceiptService;
        this.staminaProjectionService = staminaProjectionService;
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
        return addItemWithResult(playerId, itemKey, quantity).totalQuantity();
    }

    @Override
    @Transactional
    public ItemGrantResult addItemWithResult(Long playerId, String itemKey, int quantity) {
        if (quantity <= 0) return new ItemGrantResult(0, 0);

        QueryWrapper<Item> itemQuery = new QueryWrapper<>();
        itemQuery.eq("item_key", itemKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(itemQuery);
        if (item == null) return new ItemGrantResult(0, 0);

        int maxStack = Math.max(0, item.getMaxStack() != null ? item.getMaxStack() : 999);

        QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
        invQuery.eq("player_id", playerId).eq("item_key", itemKey);
        invQuery.last("FOR UPDATE");
        PlayerInventory existing = inventoryMapper.selectOne(invQuery);

        int previousQuantity = existing == null || existing.getQuantity() == null
                ? 0 : Math.max(0, existing.getQuantity());
        int quantityGranted = Math.min(quantity, Math.max(0, maxStack - previousQuantity));
        if (quantityGranted == 0) return new ItemGrantResult(0, previousQuantity, item.getName());

        int newQuantity = previousQuantity + quantityGranted;
        if (existing != null) {
            existing.setQuantity(newQuantity);
            if (inventoryMapper.updateById(existing) <= 0) {
                return new ItemGrantResult(0, previousQuantity, item.getName());
            }
            return new ItemGrantResult(quantityGranted, newQuantity, item.getName());
        } else {
            PlayerInventory inv = new PlayerInventory();
            inv.setPlayerId(playerId);
            inv.setItemKey(itemKey);
            inv.setQuantity(newQuantity);
            if (inventoryMapper.insert(inv) <= 0) {
                return new ItemGrantResult(0, 0, item.getName());
            }
            return new ItemGrantResult(quantityGranted, newQuantity, item.getName());
        }
    }

    @Override
    @Transactional
    public boolean removeItem(Long playerId, String itemKey, int quantity) {
        if (quantity <= 0) return true;

        QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
        invQuery.eq("player_id", playerId).eq("item_key", itemKey);
        invQuery.last("FOR UPDATE");
        PlayerInventory existing = inventoryMapper.selectOne(invQuery);
        if (existing == null) return false;
        if (existing.getQuantity() < quantity) return false;

        int newQty = existing.getQuantity() - quantity;
        if (newQty <= 0) {
            return inventoryMapper.deleteById(existing) > 0;
        } else {
            existing.setQuantity(newQty);
            return inventoryMapper.updateById(existing) > 0;
        }
    }

    @Override
    @Transactional
    public UseItemResultVO useItem(Long playerId, String itemKey, Long userId, String requestKey) {
        Player player = lockOwnedPlayer(playerId, userId);
        Map<String, Object> request = actionPayload("playerId", playerId, "itemKey", itemKey);
        UseItemResultVO replay = actionReceiptService.findReplay(
                playerId, "inventory.use", requestKey, request, UseItemResultVO.class);
        if (replay != null) return replay;

        UseItemResultVO result = useItemOnce(player, itemKey);
        actionReceiptService.record(playerId, "inventory.use", requestKey, request, result);
        return result;
    }

    private UseItemResultVO useItemOnce(Player player, String itemKey) {
        Long playerId = player.getId();

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
        invQuery.last("FOR UPDATE");
        PlayerInventory inventory = inventoryMapper.selectOne(invQuery);
        if (inventory == null || inventory.getQuantity() < 1)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "背包中无此物品或数量不足: " + itemKey);
        int inventoryQuantityBefore = inventory.getQuantity();

        // 解析效果
        Map<String, Object> effects = parseJsonMap(item.getEffectsJson());
        if (effects.isEmpty())
            throw new BusinessException(ErrorCode.INVALID_FORMAT, "物品没有配置可用效果，未消耗: " + itemKey);
        Map<String, Object> applied = new LinkedHashMap<>();

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> statsBefore = new LinkedHashMap<>(stats);
        int fragmentsBefore = player.getStoryFragments() != null ? player.getStoryFragments() : 0;
        int abyssMarkBefore = player.getAbyssMark() != null ? player.getAbyssMark() : 0;

        for (Map.Entry<String, Object> entry : effects.entrySet()) {
            String key = entry.getKey();
            int val = toInt(entry.getValue(), 0);

            switch (key) {
                case "restore_stamina": {
                    int current = toInt(stats.get("stamina"), 0);
                    int maxStamina = staminaProjectionService.projectMaxStamina(
                            player.getId(), toInt(stats.get("maxStamina"), 100));
                    int added = Math.max(0, Math.min(val, maxStamina - current));
                    stats.put("stamina", current + added);
                    applied.put("stamina", added);
                    break;
                }
                case "heal_hp": {
                    int current = toInt(stats.get("hp"), 0);
                    int maxHp = toInt(stats.get("maxHp"), 100);
                    int healed = Math.max(0, Math.min(val, maxHp - current));
                    stats.put("hp", current + healed);
                    applied.put("hp", healed);
                    break;
                }
                case "story_fragments":
                    player.setStoryFragments(ResourceBalance.applyDelta(
                            "故事碎片", player.getStoryFragments(), val));
                    applied.put("storyFragments", val);
                    break;
                case "channel_heat": {
                    int gained = playerEffectService.applyChannelHeatGainRate(playerId, val);
                    int current = toInt(stats.get("channelHeat"), 0);
                    stats.put("channelHeat", ResourceBalance.applySignedDelta("星流热度", current, gained));
                    applied.put("channelHeat", gained);
                    break;
                }
                case "abyss_mark":
                    player.setAbyssMark(player.getAbyssMark() + val);
                    applied.put("abyssMark", val);
                    break;
                case "world_line_shift": {
                    int current = toInt(stats.get("worldLineShift"), 0);
                    stats.put("worldLineShift", ResourceBalance.applySignedDelta("世界线偏移", current, val));
                    applied.put("worldLineShift", val);
                    break;
                }
                default:
                    throw new BusinessException(ErrorCode.INVALID_FORMAT,
                            "物品效果暂不支持，未消耗: " + key);
            }
        }

        boolean hasEffectiveBenefit = applied.values().stream()
                .anyMatch(value -> toInt(value, 0) > 0);
        if (!hasEffectiveBenefit)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                    "当前状态无法获得该物品效果，物品未消耗");

        // 消耗物品
        int newQty = inventory.getQuantity() - 1;
        if (newQty <= 0) {
            if (inventoryMapper.deleteById(inventory) <= 0)
                throw new IllegalStateException("消耗物品失败: " + itemKey);
        } else {
            inventory.setQuantity(newQty);
            if (inventoryMapper.updateById(inventory) <= 0)
                throw new IllegalStateException("更新物品数量失败: " + itemKey);
        }

        // 保存玩家
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception exception) {
            throw new IllegalStateException("物品效果序列化失败: " + itemKey, exception);
        }
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存物品效果失败: " + itemKey);

        String transactionGroupId = UUID.randomUUID().toString();
        Map<String, Object> resourceMetadata = Map.of(
                "itemKey", itemKey, "itemName", Objects.toString(item.getName(), ""));
        recordResourceMutation(playerId, "item", itemKey, inventoryQuantityBefore, newQty,
                "item_use", itemKey, transactionGroupId, transactionGroupId + ":item:" + itemKey,
                resourceMetadata);
        int fragmentsAfter = player.getStoryFragments() != null ? player.getStoryFragments() : 0;
        recordResourceMutation(playerId, "currency", "story_fragments", fragmentsBefore,
                fragmentsAfter, "item_use", itemKey, transactionGroupId,
                transactionGroupId + ":currency:story_fragments", resourceMetadata);
        int abyssMarkAfter = player.getAbyssMark() != null ? player.getAbyssMark() : 0;
        recordResourceMutation(playerId, "progress", "abyss_mark", abyssMarkBefore,
                abyssMarkAfter, "item_use", itemKey, transactionGroupId,
                transactionGroupId + ":progress:abyss_mark", resourceMetadata);
        recordStatMutation(playerId, "energy", "stamina", statsBefore, stats,
                itemKey, transactionGroupId, resourceMetadata);
        recordStatMutation(playerId, "vitality", "hp", statsBefore, stats,
                itemKey, transactionGroupId, resourceMetadata);
        recordStatMutation(playerId, "progress", "channelHeat", statsBefore, stats,
                itemKey, transactionGroupId, resourceMetadata);
        recordStatMutation(playerId, "progress", "worldLineShift", statsBefore, stats,
                itemKey, transactionGroupId, resourceMetadata);

        // 日志
        Map<String, Object> logMetadata = new LinkedHashMap<>();
        logMetadata.put("kind", "item_use");
        logMetadata.put("itemKey", item.getItemKey());
        logMetadata.put("itemName", item.getName());
        logMetadata.put("appliedEffects", new LinkedHashMap<>(applied));
        playerLogService.addLog(playerId, "item_use", "使用了 " + item.getName(), logMetadata);

        UseItemResultVO result = new UseItemResultVO();
        result.setItemKey(item.getItemKey());
        result.setItemName(item.getName());
        result.setRemainingQuantity(newQty);
        result.setAppliedEffects(applied);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> useItems(Long playerId, String itemKey, int quantity,
                                        Long userId, String requestKey) {
        Player player = lockOwnedPlayer(playerId, userId);
        Map<String, Object> request = actionPayload(
                "playerId", playerId, "itemKey", itemKey, "quantity", quantity);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "inventory.use.batch", requestKey, request, Map.class);
        if (replay != null) return replay;

        int used = 0;
        // Keep the legacy partial-success behavior, but bound work per request.
        for (int i = 0; i < Math.max(0, Math.min(quantity, 99)); i++) {
            try {
                useItemOnce(player, itemKey);
                used++;
            } catch (BusinessException expectedStop) {
                break;
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("used", used);
        response.put("itemKey", itemKey);
        actionReceiptService.record(playerId, "inventory.use.batch", requestKey, request, response);
        return response;
    }

    @Override
    @Transactional
    public SellItemResultVO sellItem(Long playerId, String itemKey, int quantity,
                                     Long userId, String requestKey) {
        Player player = lockOwnedPlayer(playerId, userId);
        Map<String, Object> request = actionPayload(
                "playerId", playerId, "itemKey", itemKey, "quantity", quantity);
        SellItemResultVO replay = actionReceiptService.findReplay(
                playerId, "inventory.sell", requestKey, request, SellItemResultVO.class);
        if (replay != null) return replay;

        SellItemResultVO result = sellItemOnce(player, itemKey, quantity);
        actionReceiptService.record(playerId, "inventory.sell", requestKey, request, result);
        return result;
    }

    private SellItemResultVO sellItemOnce(Player player, String itemKey, int quantity) {
        Long playerId = player.getId();
        if (quantity <= 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "出售数量必须大于0");

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
        invQuery.last("FOR UPDATE");
        PlayerInventory inventory = inventoryMapper.selectOne(invQuery);
        if (inventory == null || inventory.getQuantity() < quantity)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "背包中无此物品或数量不足: " + itemKey);

        int itemQuantityBefore = inventory.getQuantity();
        int actualQty = Math.min(quantity, itemQuantityBefore);
        int newQty = inventory.getQuantity() - actualQty;

        int coinsBefore = player.getCoins() != null ? player.getCoins() : 0;
        long totalCoinsLong = Math.multiplyExact((long) sellPrice, actualQty);
        int coinsAfter = ResourceBalance.applyDelta("星币", coinsBefore, totalCoinsLong);
        int totalCoins = Math.toIntExact(totalCoinsLong);

        if (newQty <= 0) {
            if (inventoryMapper.deleteById(inventory) <= 0)
                throw new IllegalStateException("出售物品失败: " + itemKey);
        } else {
            inventory.setQuantity(newQty);
            if (inventoryMapper.updateById(inventory) <= 0)
                throw new IllegalStateException("更新出售物品数量失败: " + itemKey);
        }

        player.setCoins(coinsAfter);
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("出售物品星币入账失败");

        String transactionGroupId = UUID.randomUUID().toString();
        Map<String, Object> resourceMetadata = new LinkedHashMap<>();
        resourceMetadata.put("itemName", item.getName());
        resourceMetadata.put("quantity", actualQty);
        resourceMetadata.put("sellPrice", sellPrice);
        recordResourceMutation(playerId, "item", itemKey, itemQuantityBefore,
                newQty, "item_sell", itemKey, transactionGroupId,
                transactionGroupId + ":item:" + itemKey, resourceMetadata);
        recordResourceMutation(playerId, "currency", "coins", coinsBefore, coinsAfter,
                "item_sell", itemKey, transactionGroupId,
                transactionGroupId + ":currency:coins", resourceMetadata);

        playerLogService.addLog(playerId, "item_sell",
                String.format("出售了 %d 个 %s，获得 %d 星币", actualQty, item.getName(), totalCoins));

        SellItemResultVO result = new SellItemResultVO();
        result.setItemKey(item.getItemKey());
        result.setItemName(item.getName());
        result.setSellQuantity(actualQty);
        result.setSellPrice(sellPrice);
        result.setTotalCoinsGained(totalCoins);
        result.setRemainingQuantity(newQty);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> sellItems(Long playerId, List<SellItemCommand> items,
                                         Long userId, String requestKey) {
        Player player = lockOwnedPlayer(playerId, userId);
        Map<String, Object> request = actionPayload(
                "playerId", playerId, "items", items == null ? List.of() : items);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "inventory.sell.batch", requestKey, request, Map.class);
        if (replay != null) return replay;

        int totalCoins = 0;
        List<String> soldItems = new ArrayList<>();
        for (SellItemCommand command : items) {
            SellItemResultVO result = sellItemOnce(player, command.itemKey(), command.quantity());
            if (result.getSellQuantity() > 0) {
                totalCoins = Math.addExact(totalCoins, result.getTotalCoinsGained());
                soldItems.add(command.itemKey());
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("coins", totalCoins);
        response.put("items", soldItems);
        actionReceiptService.record(playerId, "inventory.sell.batch", requestKey, request, response);
        return response;
    }

    // ─── 工具方法 ───

    private Player lockOwnedPlayer(Long playerId, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        return player;
    }

    private Map<String, Object> actionPayload(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("操作请求内容必须为键值对");
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }

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
            Map<String, Object> ingredients = parseJsonMap(r.getIngredientsJson());
            map.put("ingredients", ingredients);
            map.put("ingredientSummary", deriveIngredientSummary(ingredients));
            map.put("costCoins", r.getCostCoins());
            // 查询合成结果物品的效果
            map.put("resultEffects", deriveResultEffects(r.getResultItemKey()));
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> synthesize(Long playerId, String recipeKey,
                                          Long userId, String requestKey) {
        Player player = lockOwnedPlayer(playerId, userId);
        Map<String, Object> request = actionPayload("playerId", playerId, "recipeKey", recipeKey);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "inventory.synthesize", requestKey, request, Map.class);
        if (replay != null) return replay;

        Map<String, Object> response = synthesizeOnce(player, recipeKey);
        actionReceiptService.record(playerId, "inventory.synthesize", requestKey, request, response);
        return response;
    }

    private Map<String, Object> synthesizeOnce(Player player, String recipeKey) {
        Long playerId = player.getId();

        String transactionGroupId = UUID.randomUUID().toString();

        QueryWrapper<SynthesisRecipe> recipeQuery = new QueryWrapper<>();
        recipeQuery.eq("recipe_key", recipeKey).eq("enabled", 1);
        SynthesisRecipe recipe = recipeMapper.selectOne(recipeQuery);
        if (recipe == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "合成配方不存在: " + recipeKey);

        // 解析所需材料
        Map<String, Object> ingredients = parseJsonMap(recipe.getIngredientsJson());
        if (ingredients.isEmpty())
            throw new BusinessException(ErrorCode.INVALID_FORMAT, "配方材料配置错误: " + recipeKey);

        // 配方材料既可能是普通背包物品，也可能是玩家装备；先完整校验并锁定所有材料，避免扣到一半才发现不足。
        Map<String, PlayerInventory> itemIngredients = new LinkedHashMap<>();
        Map<String, PlayerEquipment> equipmentIngredients = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ingredients.entrySet()) {
            String requiredItemKey = entry.getKey();
            int requiredQty = toInt(entry.getValue(), 0);
            if (requiredQty <= 0)
                throw new BusinessException(ErrorCode.INVALID_FORMAT,
                        "配方材料数量配置错误: " + recipeKey + "/" + requiredItemKey);

            QueryWrapper<Item> materialItemQuery = new QueryWrapper<>();
            materialItemQuery.eq("item_key", requiredItemKey).eq("enabled", 1);
            Item materialItem = itemMapper.selectOne(materialItemQuery);

            QueryWrapper<Equipment> materialEquipmentQuery = new QueryWrapper<>();
            materialEquipmentQuery.eq("equipment_key", requiredItemKey).eq("enabled", 1);
            Equipment materialEquipment = equipmentMapper.selectOne(materialEquipmentQuery);

            if (materialItem != null && materialEquipment != null)
                throw new BusinessException(ErrorCode.INVALID_FORMAT,
                        "配方材料同时配置为物品和装备: " + requiredItemKey);

            if (materialItem != null) {
                QueryWrapper<PlayerInventory> invQuery = new QueryWrapper<>();
                invQuery.eq("player_id", playerId).eq("item_key", requiredItemKey);
                invQuery.last("FOR UPDATE");
                PlayerInventory inventory = inventoryMapper.selectOne(invQuery);
                int owned = inventory != null && inventory.getQuantity() != null
                        ? inventory.getQuantity() : 0;
                if (owned < requiredQty) {
                    throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH,
                            String.format("材料不足: %s，需要 %d 个，拥有 %d 个",
                                    materialItem.getName(), requiredQty, owned));
                }
                itemIngredients.put(requiredItemKey, inventory);
            } else if (materialEquipment != null) {
                if (requiredQty != 1)
                    throw new BusinessException(ErrorCode.INVALID_FORMAT,
                            "装备材料每种最多配置 1 件: " + recipeKey + "/" + requiredItemKey);

                QueryWrapper<PlayerEquipment> ownedQuery = new QueryWrapper<>();
                ownedQuery.eq("player_id", playerId).eq("equipment_key", requiredItemKey);
                ownedQuery.last("FOR UPDATE");
                PlayerEquipment ownedEquipment = playerEquipmentMapper.selectOne(ownedQuery);
                if (ownedEquipment == null) {
                    throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH,
                            "材料不足: " + materialEquipment.getName() + "，需要 1 件，拥有 0 件");
                }
                if (Integer.valueOf(1).equals(ownedEquipment.getEquipped()))
                    throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                            "已穿戴的装备不能作为合成材料: " + materialEquipment.getName());
                if (Integer.valueOf(1).equals(ownedEquipment.getListed()))
                    throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                            "已上架交易的装备不能作为合成材料: " + materialEquipment.getName());
                equipmentIngredients.put(requiredItemKey, ownedEquipment);
            } else {
                throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH,
                        "配方材料不存在或已禁用: " + requiredItemKey);
            }
        }

        // 检查星币
        int costCoins = recipe.getCostCoins() != null ? recipe.getCostCoins() : 0;
        if (costCoins < 0)
            throw new BusinessException(ErrorCode.INVALID_FORMAT, "配方星币消耗配置错误: " + recipeKey);
        if (player.getCoins() < costCoins)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    String.format("星币不足: 需要 %d，拥有 %d", costCoins, player.getCoins()));

        String resultItemKey = recipe.getResultItemKey();
        int resultQty = recipe.getResultQuantity() != null ? recipe.getResultQuantity() : 1;
        if (resultItemKey == null || resultItemKey.isBlank() || resultQty <= 0)
            throw new BusinessException(ErrorCode.INVALID_FORMAT, "配方产物配置错误: " + recipeKey);

        QueryWrapper<Item> resultItemQuery = new QueryWrapper<>();
        resultItemQuery.eq("item_key", resultItemKey).eq("enabled", 1);
        Item resultItem = itemMapper.selectOne(resultItemQuery);

        QueryWrapper<Equipment> resultEquipmentQuery = new QueryWrapper<>();
        resultEquipmentQuery.eq("equipment_key", resultItemKey).eq("enabled", 1);
        Equipment resultEquipment = equipmentMapper.selectOne(resultEquipmentQuery);
        if (resultItem != null && resultEquipment != null)
            throw new BusinessException(ErrorCode.INVALID_FORMAT, "配方产物同时配置为物品和装备: " + resultItemKey);
        if (resultItem == null && resultEquipment == null)
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "配方产物不存在或已禁用: " + resultItemKey);

        if (resultEquipment != null) {
            QueryWrapper<PlayerEquipment> ownedQuery = new QueryWrapper<>();
            ownedQuery.eq("player_id", playerId).eq("equipment_key", resultItemKey);
            if (playerEquipmentMapper.selectOne(ownedQuery) != null)
                throw new BusinessException(ErrorCode.EQUIP_ALREADY_OWNED,
                        "已拥有该装备，不能重复合成: " + resultEquipment.getName());
        } else {
            int maxStack = Math.max(0, resultItem.getMaxStack() != null ? resultItem.getMaxStack() : 999);
            QueryWrapper<PlayerInventory> resultInventoryQuery = new QueryWrapper<>();
            resultInventoryQuery.eq("player_id", playerId).eq("item_key", resultItemKey);
            PlayerInventory resultInventory = inventoryMapper.selectOne(resultInventoryQuery);
            int currentResultQuantity = resultInventory == null || resultInventory.getQuantity() == null
                    ? 0 : Math.max(0, resultInventory.getQuantity());
            if ((long) currentResultQuantity + resultQty > maxStack)
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                        "背包堆叠空间不足: " + resultItem.getName());
        }

        // 扣除普通物品材料
        Map<String, Integer> itemBalancesBefore = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerInventory> entry : itemIngredients.entrySet()) {
            itemBalancesBefore.put(entry.getKey(), entry.getValue().getQuantity());
        }
        for (Map.Entry<String, PlayerInventory> entry : itemIngredients.entrySet()) {
            String requiredItemKey = entry.getKey();
            int requiredQty = toInt(ingredients.get(requiredItemKey), 0);
            if (!removeItem(playerId, requiredItemKey, requiredQty)) {
                // 不要让 synthesizeAll 捕获后提交已扣掉的前序材料；这是并发/持久化异常，必须回滚整笔事务。
                throw new IllegalStateException("合成材料在校验后发生变化，已中止: " + requiredItemKey);
            }
        }

        // 装备只能整件投入合成；已在上方锁定，且穿戴/挂单装备不会进入此列表。
        for (Map.Entry<String, PlayerEquipment> entry : equipmentIngredients.entrySet()) {
            if (playerEquipmentMapper.deleteById(entry.getValue()) <= 0)
                throw new IllegalStateException("合成装备材料在校验后发生变化，已中止: " + entry.getKey());
        }

        // 扣除星币
        int coinsBefore = player.getCoins() != null ? player.getCoins() : 0;
        int coinsAfter = coinsBefore;
        if (costCoins > 0) {
            coinsAfter = Math.subtractExact(coinsBefore, costCoins);
            player.setCoins(coinsAfter);
            if (playerMapper.updateById(player) <= 0)
                throw new IllegalStateException("保存合成星币扣除失败");
        }

        // 按产物真实类型发放；DB 写入失败时抛出非业务异常，避免 synthesizeAll 捕获后误提交材料扣除。
        ItemGrantResult resultItemGrant = null;
        if (resultEquipment != null) {
            if (equipmentService.addEquipment(playerId, resultItemKey, "synthesis") <= 0)
                throw new IllegalStateException("合成装备发放失败: " + resultItemKey);
        } else {
            resultItemGrant = addItemWithResult(playerId, resultItemKey, resultQty);
            if (resultItemGrant.quantityGranted() != resultQty)
                throw new IllegalStateException("合成物品发放不完整: " + resultItemKey);
        }

        Map<String, Object> resourceMetadata = Map.of("recipeKey", recipeKey);
        for (Map.Entry<String, Integer> entry : itemBalancesBefore.entrySet()) {
            int requiredQty = toInt(ingredients.get(entry.getKey()), 0);
            recordResourceMutation(playerId, "item", entry.getKey(), entry.getValue(),
                    entry.getValue() - requiredQty, "synthesis", recipeKey, transactionGroupId,
                    transactionGroupId + ":material:item:" + entry.getKey(), resourceMetadata);
        }
        for (String equipmentKey : equipmentIngredients.keySet()) {
            recordResourceMutation(playerId, "equipment", equipmentKey, 1, 0,
                    "synthesis", recipeKey, transactionGroupId,
                    transactionGroupId + ":material:equipment:" + equipmentKey, resourceMetadata);
        }
        recordResourceMutation(playerId, "currency", "coins", coinsBefore, coinsAfter,
                "synthesis", recipeKey, transactionGroupId,
                transactionGroupId + ":currency:coins", resourceMetadata);
        if (resultEquipment != null) {
            recordResourceMutation(playerId, "equipment", resultItemKey, 0, 1,
                    "synthesis", recipeKey, transactionGroupId,
                    transactionGroupId + ":result:equipment:" + resultItemKey, resourceMetadata);
        } else {
            int itemBalanceAfter = resultItemGrant.totalQuantity();
            recordResourceMutation(playerId, "item", resultItemKey,
                    itemBalanceAfter - resultItemGrant.quantityGranted(), itemBalanceAfter,
                    "synthesis", recipeKey, transactionGroupId,
                    transactionGroupId + ":result:item:" + resultItemKey, resourceMetadata);
        }

        String resultName = resultEquipment != null ? resultEquipment.getName() : resultItem.getName();

        playerLogService.addLog(playerId, "synthesis",
                String.format("合成了 %d 个 %s（配方: %s）", resultQty, resultName, recipe.getName()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("recipe", recipeKey);
        response.put("resultItemKey", resultItemKey);
        response.put("resultItemName", resultName);
        response.put("resultType", resultEquipment != null ? "equipment" : "item");
        response.put("resultQuantity", resultQty);
        response.put("costCoins", costCoins);
        return response;
    }

    private void recordStatMutation(Long playerId, String resourceType, String resourceKey,
                                    Map<String, Object> beforeStats, Map<String, Object> afterStats,
                                    String itemKey, String transactionGroupId,
                                    Map<String, Object> metadata) {
        long before = toInt(beforeStats.get(resourceKey), 0);
        long after = toInt(afterStats.get(resourceKey), Math.toIntExact(before));
        String ledgerResourceKey = switch (resourceKey) {
            case "channelHeat" -> "channel_heat";
            case "worldLineShift" -> "world_line_shift";
            default -> resourceKey;
        };
        recordResourceMutation(playerId, resourceType, ledgerResourceKey, before, after,
                "item_use", itemKey, transactionGroupId,
                transactionGroupId + ":" + resourceType + ":" + ledgerResourceKey, metadata);
    }

    private void recordResourceMutation(Long playerId, String resourceType, String resourceKey,
                                        long balanceBefore, long balanceAfter, String sourceType,
                                        String sourceId, String transactionGroupId,
                                        String idempotencyKey, Map<String, Object> metadata) {
        long amount = balanceAfter - balanceBefore;
        if (amount == 0) return;
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, balanceBefore, balanceAfter,
                sourceType, sourceId, transactionGroupId, idempotencyKey, metadata));
    }

    @Override
    @Transactional
    public Map<String, Object> synthesizeAll(Long playerId, String recipeKey, int times,
                                             Long userId, String requestKey) {
        if (times <= 0) times = 1;
        if (times > 99) times = 99;
        Player player = lockOwnedPlayer(playerId, userId);
        Map<String, Object> request = actionPayload(
                "playerId", playerId, "recipeKey", recipeKey, "times", times);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "inventory.synthesize.batch", requestKey, request, Map.class);
        if (replay != null) return replay;

        int successCount = 0;
        String lastError = null;
        for (int i = 0; i < times; i++) {
            try {
                synthesizeOnce(player, recipeKey);
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
        actionReceiptService.record(
                playerId, "inventory.synthesize.batch", requestKey, request, response);
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

    private String deriveIngredientSummary(Map<String, Object> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "无";
        return ingredients.entrySet().stream()
                .map(entry -> deriveIngredientName(entry.getKey()) + " ×" + toInt(entry.getValue(), 0))
                .collect(java.util.stream.Collectors.joining(" + "));
    }

    private String deriveIngredientName(String ingredientKey) {
        QueryWrapper<Item> itemQuery = new QueryWrapper<>();
        itemQuery.eq("item_key", ingredientKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(itemQuery);
        if (item != null && item.getName() != null && !item.getName().isBlank()) return item.getName();

        QueryWrapper<Equipment> equipmentQuery = new QueryWrapper<>();
        equipmentQuery.eq("equipment_key", ingredientKey).eq("enabled", 1);
        Equipment equipment = equipmentMapper.selectOne(equipmentQuery);
        return equipment != null && equipment.getName() != null && !equipment.getName().isBlank()
                ? equipment.getName() : ingredientKey;
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
