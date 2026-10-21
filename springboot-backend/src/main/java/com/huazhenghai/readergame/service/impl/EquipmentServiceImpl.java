package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Equipment;
import com.huazhenghai.readergame.entity.EquipmentSet;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerEquipment;
import com.huazhenghai.readergame.mapper.EquipmentMapper;
import com.huazhenghai.readergame.mapper.EquipmentSetMapper;
import com.huazhenghai.readergame.mapper.PlayerEquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.EquipmentGrantResult;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.EquipmentVO;
import com.huazhenghai.readergame.vo.PlayerEquipmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EquipmentServiceImpl implements EquipmentService {

    private static final int MAX_ENHANCEMENT_LEVEL = 10;
    private static final double ENHANCEMENT_BONUS_PER_LEVEL = 0.05;

    private final PlayerMapper playerMapper;
    private final EquipmentMapper equipmentMapper;
    private final EquipmentSetMapper equipmentSetMapper;
    private final PlayerEquipmentMapper playerEquipmentMapper;
    private final PlayerLogService playerLogService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final PlayerActionReceiptService actionReceiptService;
    private final ObjectMapper objectMapper;

    public EquipmentServiceImpl(PlayerMapper playerMapper,
                                EquipmentMapper equipmentMapper,
                                EquipmentSetMapper equipmentSetMapper,
                                PlayerEquipmentMapper playerEquipmentMapper,
                                PlayerLogService playerLogService,
                                PlayerResourceTransactionService resourceTransactionService,
                                PlayerActionReceiptService actionReceiptService,
                                ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.equipmentMapper = equipmentMapper;
        this.equipmentSetMapper = equipmentSetMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.playerLogService = playerLogService;
        this.resourceTransactionService = resourceTransactionService;
        this.actionReceiptService = actionReceiptService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EquipmentVO> getAllEquipment() {
        QueryWrapper<Equipment> query = new QueryWrapper<>();
        query.eq("enabled", 1);
        return equipmentMapper.selectList(query).stream()
                .map(this::toEquipmentVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlayerEquipmentVO> getPlayerEquipment(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的装备");

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId);
        List<PlayerEquipment> peList = playerEquipmentMapper.selectList(peQuery);

        List<PlayerEquipmentVO> result = new ArrayList<>();
        for (PlayerEquipment pe : peList) {
            QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
            eqQuery.eq("equipment_key", pe.getEquipmentKey()).eq("enabled", 1);
            Equipment equip = equipmentMapper.selectOne(eqQuery);
            if (equip == null) continue;

            result.add(toPlayerEquipmentVO(pe, equip));
        }
        return result;
    }

    @Override
    public Map<String, PlayerEquipmentVO> getEquippedSlots(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的装备");

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipped", 1);
        List<PlayerEquipment> peList = playerEquipmentMapper.selectList(peQuery);

        Map<String, PlayerEquipmentVO> slots = new LinkedHashMap<>();
        for (PlayerEquipment pe : peList) {
            QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
            eqQuery.eq("equipment_key", pe.getEquipmentKey()).eq("enabled", 1);
            Equipment equip = equipmentMapper.selectOne(eqQuery);
            if (equip == null) continue;
            slots.put(equip.getSlot(), toPlayerEquipmentVO(pe, equip));
        }
        return slots;
    }

    @Override
    public Map<String, Object> calculateEquipmentBonus(Long playerId) {
        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipped", 1).gt("durability", 0);
        List<PlayerEquipment> peList = playerEquipmentMapper.selectList(peQuery);

        Map<String, Object> bonus = new LinkedHashMap<>();
        for (PlayerEquipment pe : peList) {
            QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
            eqQuery.eq("equipment_key", pe.getEquipmentKey()).eq("enabled", 1);
            Equipment equip = equipmentMapper.selectOne(eqQuery);
            if (equip == null) continue;

            Map<String, Object> stats = enhancedStats(equip, pe);
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                String key = entry.getKey();
                double val = toDouble(entry.getValue(), 0);
                double current = toDouble(bonus.get(key), 0);
                bonus.put(key, current + val);
            }
        }
        return bonus;
    }

    @Override
    public Map<String, Object> calculateEquipmentSpecialEffectBonuses(Long playerId) {
        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipped", 1).gt("durability", 0);
        List<PlayerEquipment> peList = playerEquipmentMapper.selectList(peQuery);

        Map<String, Object> bonuses = new LinkedHashMap<>();
        for (PlayerEquipment playerEquipment : peList) {
            QueryWrapper<Equipment> equipmentQuery = new QueryWrapper<>();
            equipmentQuery.eq("equipment_key", playerEquipment.getEquipmentKey()).eq("enabled", 1);
            Equipment equipment = equipmentMapper.selectOne(equipmentQuery);
            if (equipment == null) continue;

            Map<String, Object> specialEffects = parseJsonMap(equipment.getSpecialEffectsJson());
            for (Map.Entry<String, Object> entry : specialEffects.entrySet()) {
                if (!(entry.getValue() instanceof Number value)) continue;
                double effect = value.doubleValue();
                if (!Double.isFinite(effect)) continue;

                double current = bonuses.get(entry.getKey()) instanceof Number total
                        ? total.doubleValue() : 0.0;
                double combined = current + effect;
                if (Double.isFinite(combined)) bonuses.put(entry.getKey(), combined);
            }
        }
        return bonuses;
    }

    @Override
    public Map<String, String> getEquipmentBonusDetail(Long playerId) {
        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipped", 1).gt("durability", 0);
        List<PlayerEquipment> peList = playerEquipmentMapper.selectList(peQuery);

        Map<String, StringBuilder> detail = new LinkedHashMap<>();
        for (PlayerEquipment pe : peList) {
            QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
            eqQuery.eq("equipment_key", pe.getEquipmentKey()).eq("enabled", 1);
            Equipment equip = equipmentMapper.selectOne(eqQuery);
            if (equip == null) continue;

            String equipName = equip.getName() != null ? equip.getName() : pe.getEquipmentKey();
            Map<String, Object> stats = enhancedStats(equip, pe);
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                String key = entry.getKey();
                double val = toDouble(entry.getValue(), 0);
                if (val == 0) continue;
                detail.computeIfAbsent(key, k -> new StringBuilder())
                        .append(detail.get(key).isEmpty() ? "" : ", ")
                        .append(equipName).append("+").append(formatNum(val));
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> e : detail.entrySet()) {
            result.put(e.getKey(), e.getValue().toString());
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getEquipmentSets() {
        QueryWrapper<EquipmentSet> setQuery = new QueryWrapper<>();
        setQuery.eq("enabled", 1).orderByAsc("id");
        List<Map<String, Object>> result = new ArrayList<>();
        for (EquipmentSet set : equipmentSetMapper.selectList(setQuery)) {
            Map<String, Object> item = toSetMap(set);
            QueryWrapper<Equipment> equipmentQuery = new QueryWrapper<>();
            equipmentQuery.eq("set_key", set.getSetKey()).eq("enabled", 1).orderByAsc("id");
            List<Map<String, Object>> pieces = equipmentMapper.selectList(equipmentQuery).stream()
                    .map(equipment -> {
                        Map<String, Object> piece = new LinkedHashMap<>();
                        piece.put("equipmentKey", equipment.getEquipmentKey());
                        piece.put("name", equipment.getName());
                        piece.put("slot", equipment.getSlot());
                        piece.put("rarity", equipment.getRarity());
                        return piece;
                    }).collect(Collectors.toList());
            item.put("pieces", pieces);
            item.put("pieceCount", pieces.size());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getPlayerSetBonuses(Long playerId) {
        QueryWrapper<PlayerEquipment> equippedQuery = new QueryWrapper<>();
        equippedQuery.eq("player_id", playerId).eq("equipped", 1).gt("durability", 0);
        Map<String, Integer> equippedCounts = new LinkedHashMap<>();
        for (PlayerEquipment playerEquipment : playerEquipmentMapper.selectList(equippedQuery)) {
            Equipment equipment = equipmentMapper.selectOne(new QueryWrapper<Equipment>()
                    .eq("equipment_key", playerEquipment.getEquipmentKey()).eq("enabled", 1));
            if (equipment == null || equipment.getSetKey() == null || equipment.getSetKey().isBlank()) continue;
            equippedCounts.merge(equipment.getSetKey(), 1, Integer::sum);
        }

        Map<String, Object> totalBonuses = new LinkedHashMap<>();
        List<Map<String, Object>> activeSets = new ArrayList<>();
        if (!equippedCounts.isEmpty()) {
            List<EquipmentSet> sets = equipmentSetMapper.selectList(new QueryWrapper<EquipmentSet>()
                    .eq("enabled", 1).in("set_key", equippedCounts.keySet()));
            for (EquipmentSet set : sets) {
                int equippedCount = equippedCounts.getOrDefault(set.getSetKey(), 0);
                Map<String, Object> activeBonus = resolveActiveSetBonus(set.getBonusesJson(), equippedCount);
                if (activeBonus.isEmpty()) continue;

                Map<String, Object> active = toSetMap(set);
                active.put("equippedCount", equippedCount);
                active.put("bonuses", activeBonus);
                activeSets.add(active);
                mergeNumericBonuses(totalBonuses, activeBonus);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("activeSets", activeSets);
        response.put("bonuses", totalBonuses);
        return response;
    }

    private Map<String, Object> toSetMap(EquipmentSet set) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", set.getId());
        item.put("setKey", set.getSetKey());
        item.put("name", set.getName());
        item.put("description", set.getDescription());
        item.put("bonuses", parseJsonMap(set.getBonusesJson()));
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveActiveSetBonus(String bonusesJson, int equippedCount) {
        Map<String, Object> definition = parseJsonMap(bonusesJson);
        if (definition.isEmpty()) return new LinkedHashMap<>();

        Map<String, Object> active = new LinkedHashMap<>();
        Object thresholds = definition.get("thresholds");
        if (thresholds instanceof List<?> thresholdList) {
            for (Object raw : thresholdList) {
                if (!(raw instanceof Map<?, ?> rawMap)) continue;
                Map<String, Object> threshold = (Map<String, Object>) rawMap;
                int required = (int) toDouble(threshold.get("count"), 0);
                if (required <= equippedCount) {
                    Object effects = threshold.containsKey("effects") ? threshold.get("effects") : threshold.get("bonuses");
                    if (effects instanceof Map<?, ?>) active = new LinkedHashMap<>((Map<String, Object>) effects);
                }
            }
            return active;
        }

        int bestRequired = 0;
        for (Map.Entry<String, Object> entry : definition.entrySet()) {
            int required;
            try { required = Integer.parseInt(entry.getKey()); }
            catch (NumberFormatException ignored) { continue; }
            if (required <= equippedCount && required >= bestRequired && entry.getValue() instanceof Map<?, ?>) {
                bestRequired = required;
                active = new LinkedHashMap<>((Map<String, Object>) entry.getValue());
            }
        }
        if (active.isEmpty() && definition.get("effects") instanceof Map<?, ?>) {
            int required = (int) toDouble(definition.get("requiredCount"), 1);
            if (equippedCount >= required) active = new LinkedHashMap<>((Map<String, Object>) definition.get("effects"));
        }
        return active;
    }

    private void mergeNumericBonuses(Map<String, Object> total, Map<String, Object> additions) {
        for (Map.Entry<String, Object> entry : additions.entrySet()) {
            if (!(entry.getValue() instanceof Number)) continue;
            double current = toDouble(total.get(entry.getKey()), 0);
            total.put(entry.getKey(), current + ((Number) entry.getValue()).doubleValue());
        }
    }

    private String formatNum(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    @Override
    @Transactional
    public int addEquipment(Long playerId, String equipmentKey, String source) {
        return addEquipmentWithResult(playerId, equipmentKey, source).quantityGranted();
    }

    @Override
    @Transactional
    public EquipmentGrantResult addEquipmentWithResult(Long playerId, String equipmentKey, String source) {
        QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("equipment_key", equipmentKey).eq("enabled", 1);
        Equipment equip = equipmentMapper.selectOne(eqQuery);
        if (equip == null) return new EquipmentGrantResult(0, null);

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        PlayerEquipment existing = playerEquipmentMapper.selectOne(peQuery);
        if (existing != null) return new EquipmentGrantResult(0, null);

        PlayerEquipment pe = new PlayerEquipment();
        pe.setPlayerId(playerId);
        pe.setEquipmentKey(equipmentKey);
        pe.setSlot(equip.getSlot());
        pe.setEquipped(0);
        pe.setDurability(equip.getMaxDurability() != null ? equip.getMaxDurability() : 100);
        pe.setEnhancementLevel(0);
        if (playerEquipmentMapper.insert(pe) <= 0) return new EquipmentGrantResult(0, null);

        if (source != null && !source.isBlank()) {
            playerLogService.addLog(playerId, "equipment_acquire", "获得装备: " + equip.getName(),
                    Map.of("equipmentKey", equipmentKey, "source", source));
        }
        return new EquipmentGrantResult(1, equip.getName());
    }

    @Override
    @Transactional
    public PlayerEquipmentVO equip(Long playerId, String equipmentKey, Long userId) {
        lockOwnedPlayerForUpdate(playerId, userId);

        QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("equipment_key", equipmentKey).eq("enabled", 1);
        Equipment equip = equipmentMapper.selectOne(eqQuery);
        if (equip == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_FOUND, "装备不存在: " + equipmentKey);

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        PlayerEquipment target = playerEquipmentMapper.selectOne(peQuery);
        if (target == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "未拥有此装备: " + equipmentKey);

        if (target.getDurability() != null && target.getDurability() <= 0)
            throw new BusinessException(ErrorCode.EQUIP_BROKEN, "装备耐久不足，请修理后再穿戴");

        if (target.getListed() != null && target.getListed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已上架交易的装备不能穿戴，请先取消挂单");

        // 卸下同槽位旧装备
        QueryWrapper<PlayerEquipment> slotQuery = new QueryWrapper<>();
        slotQuery.eq("player_id", playerId).eq("slot", equip.getSlot()).eq("equipped", 1);
        PlayerEquipment oldEquipped = playerEquipmentMapper.selectOne(slotQuery);
        if (oldEquipped != null) {
            oldEquipped.setEquipped(0);
            if (playerEquipmentMapper.updateById(oldEquipped) <= 0)
                throw new IllegalStateException("保存原装备卸下状态失败");
        }

        // 穿戴目标装备
        target.setEquipped(1);
        if (playerEquipmentMapper.updateById(target) <= 0)
            throw new IllegalStateException("保存新装备穿戴状态失败");

        playerLogService.addLog(playerId, "equipment_equip", "穿上了 " + equip.getName());

        return toPlayerEquipmentVO(target, equip);
    }

    @Override
    @Transactional
    public PlayerEquipmentVO unequip(Long playerId, String equipmentKey, Long userId) {
        lockOwnedPlayerForUpdate(playerId, userId);

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        PlayerEquipment target = playerEquipmentMapper.selectOne(peQuery);
        if (target == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "未拥有此装备: " + equipmentKey);

        if (target.getEquipped() == null || target.getEquipped() != 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "装备未穿戴");

        target.setEquipped(0);
        if (playerEquipmentMapper.updateById(target) <= 0)
            throw new IllegalStateException("保存装备卸下状态失败");

        QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("equipment_key", equipmentKey);
        Equipment equip = equipmentMapper.selectOne(eqQuery);

        playerLogService.addLog(playerId, "equipment_unequip",
                "卸下了 " + (equip != null ? equip.getName() : equipmentKey));

        return toPlayerEquipmentVO(target, equip);
    }

    @Override
    @Transactional
    public Map<String, Object> repair(Long playerId, String equipmentKey, Long userId) {
        Player player = lockOwnedPlayerForUpdate(playerId, userId);

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        PlayerEquipment target = playerEquipmentMapper.selectOne(peQuery);
        if (target == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "未拥有此装备: " + equipmentKey);

        if (target.getListed() != null && target.getListed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已上架交易的装备不能修理，请先取消挂单");

        QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("equipment_key", equipmentKey).eq("enabled", 1);
        Equipment equip = equipmentMapper.selectOne(eqQuery);
        if (equip == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_FOUND, "装备不存在: " + equipmentKey);

        int maxDura = equip.getMaxDurability() != null ? equip.getMaxDurability() : 100;
        int currentDura = target.getDurability() != null ? target.getDurability() : 0;
        if (currentDura >= maxDura)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "装备耐久已满，无需修理");

        int repairCost = equip.getRepairCost() != null ? equip.getRepairCost() : 10;
        if (player.getCoins() < repairCost)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "星币不足，修理需要 " + repairCost + " 星币");

        int coinsBefore = player.getCoins() != null ? player.getCoins() : 0;
        int coinsAfter = coinsBefore - repairCost;
        player.setCoins(coinsAfter);
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存装备修理星币扣除失败");

        target.setDurability(maxDura);
        if (playerEquipmentMapper.updateById(target) <= 0)
            throw new IllegalStateException("保存装备耐久修复结果失败");

        String transactionId = UUID.randomUUID().toString();
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, "currency", "coins", -repairCost, coinsBefore, coinsAfter,
                "equipment_repair", equipmentKey, transactionId, transactionId,
                Map.of("durabilityBefore", currentDura, "durabilityAfter", maxDura)));

        playerLogService.addLog(playerId, "equipment_repair",
                "修理了 " + equip.getName() + "（花费 " + repairCost + " 星币）",
                Map.of("equipmentKey", equipmentKey, "cost", repairCost));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipmentKey", equipmentKey);
        result.put("name", equip.getName());
        result.put("durability", maxDura);
        result.put("maxDurability", maxDura);
        result.put("cost", repairCost);
        result.put("repairCost", repairCost);
        result.put("remainingCoins", player.getCoins());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> enhance(Long playerId, String equipmentKey, Long userId, String requestKey) {
        Player player = lockOwnedPlayerForUpdate(playerId, userId);

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("playerId", playerId);
        requestPayload.put("equipmentKey", equipmentKey);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "equipment.enhance", requestKey, requestPayload, Map.class);
        if (replay != null) return replay;

        QueryWrapper<PlayerEquipment> ownedQuery = new QueryWrapper<>();
        ownedQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey).last("FOR UPDATE");
        PlayerEquipment target = playerEquipmentMapper.selectOne(ownedQuery);
        if (target == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "未拥有此装备: " + equipmentKey);
        if (target.getListed() != null && target.getListed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已上架交易的装备不能强化，请先取消挂单");

        QueryWrapper<Equipment> definitionQuery = new QueryWrapper<>();
        definitionQuery.eq("equipment_key", equipmentKey).eq("enabled", 1);
        Equipment definition = equipmentMapper.selectOne(definitionQuery);
        if (definition == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_FOUND, "装备不存在: " + equipmentKey);

        int currentLevel = Math.max(0, target.getEnhancementLevel() != null ? target.getEnhancementLevel() : 0);
        if (currentLevel >= MAX_ENHANCEMENT_LEVEL)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                    "装备已达到强化上限 +" + MAX_ENHANCEMENT_LEVEL);

        int cost = enhancementCost(definition, currentLevel);
        int currentCoins = player.getCoins() != null ? player.getCoins() : 0;
        if (currentCoins < cost)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    "星币不足，强化需要 " + cost + " 星币");

        int nextLevel = currentLevel + 1;
        int coinsAfter = Math.subtractExact(currentCoins, cost);
        player.setCoins(coinsAfter);
        target.setEnhancementLevel(nextLevel);
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存强化星币扣除失败");
        if (playerEquipmentMapper.updateById(target) <= 0)
            throw new IllegalStateException("保存装备强化等级失败");

        String transactionGroupId = "eqenh:" + playerId + ":" + UUID.nameUUIDFromBytes(
                (playerId + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> transactionMetadata = new LinkedHashMap<>();
        transactionMetadata.put("equipmentKey", equipmentKey);
        transactionMetadata.put("fromLevel", currentLevel);
        transactionMetadata.put("toLevel", nextLevel);
        transactionMetadata.put("cost", cost);
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, "currency", "coins", -cost, currentCoins, coinsAfter,
                "equipment_enhance", equipmentKey, transactionGroupId,
                transactionGroupId + ":coins", transactionMetadata));

        int bonusPercent = (int) Math.round(nextLevel * ENHANCEMENT_BONUS_PER_LEVEL * 100);
        String message = "强化了 " + definition.getName() + " 至 +" + nextLevel
                + "（基础属性 +" + bonusPercent + "%，花费 " + cost + " 星币）";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("equipmentKey", equipmentKey);
        metadata.put("fromLevel", currentLevel);
        metadata.put("toLevel", nextLevel);
        metadata.put("cost", cost);
        metadata.put("bonusPercent", bonusPercent);
        playerLogService.addLog(playerId, "equipment_enhance", message, metadata);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipmentKey", equipmentKey);
        result.put("name", definition.getName());
        result.put("fromLevel", currentLevel);
        result.put("enhancementLevel", nextLevel);
        result.put("enhancement_level", nextLevel);
        result.put("enhancementBonusPercent", bonusPercent);
        result.put("cost", cost);
        result.put("remainingCoins", coinsAfter);
        result.put("stats", enhancedStats(definition, target));
        result.put("message", message);
        actionReceiptService.record(playerId, "equipment.enhance", requestKey, requestPayload, result);
        return result;
    }

    // ─── 映射方法 ───

    private Player lockOwnedPlayerForUpdate(Long playerId, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        return player;
    }

    private EquipmentVO toEquipmentVO(Equipment e) {
        EquipmentVO vo = new EquipmentVO();
        vo.setEquipmentKey(e.getEquipmentKey());
        vo.setName(e.getName());
        vo.setSlot(e.getSlot());
        vo.setRarity(e.getRarity());
        vo.setDescription(e.getDescription());
        vo.setBaseStats(parseJsonMap(e.getBaseStatsJson()));
        vo.setSpecialEffects(parseJsonMap(e.getSpecialEffectsJson()));
        vo.setSetKey(e.getSetKey());
        vo.setMaxDurability(e.getMaxDurability() != null ? e.getMaxDurability() : 100);
        vo.setRepairCost(e.getRepairCost() != null ? e.getRepairCost() : 10);
        vo.setSellPrice(e.getSellPrice() != null ? e.getSellPrice() : 0);
        return vo;
    }

    private PlayerEquipmentVO toPlayerEquipmentVO(PlayerEquipment pe, Equipment e) {
        PlayerEquipmentVO vo = new PlayerEquipmentVO();
        vo.setEquipmentKey(pe.getEquipmentKey());
        vo.setSlot(pe.getSlot());
        vo.setEquipped(pe.getEquipped() != null && pe.getEquipped() == 1);
        vo.setDurability(pe.getDurability() != null ? pe.getDurability() : 0);
        vo.setEnhancementLevel(pe.getEnhancementLevel() != null ? pe.getEnhancementLevel() : 0);
        if (e != null) {
            vo.setName(e.getName());
            vo.setRarity(e.getRarity());
            vo.setDescription(e.getDescription());
            int enhancementLevel = Math.max(0, pe.getEnhancementLevel() != null ? pe.getEnhancementLevel() : 0);
            vo.setBaseStats(enhancedStats(e, pe));
            vo.setSpecialEffects(parseJsonMap(e.getSpecialEffectsJson()));
            vo.setSetKey(e.getSetKey());
            vo.setMaxDurability(e.getMaxDurability() != null ? e.getMaxDurability() : 100);
            vo.setRepairCost(e.getRepairCost() != null ? e.getRepairCost() : 10);
            vo.setSellPrice(e.getSellPrice() != null ? e.getSellPrice() : 0);
            vo.setMaxEnhancementLevel(MAX_ENHANCEMENT_LEVEL);
            vo.setEnhancementBonusPercent((int) Math.round(
                    enhancementLevel * ENHANCEMENT_BONUS_PER_LEVEL * 100));
            vo.setNextEnhancementCost(enhancementLevel >= MAX_ENHANCEMENT_LEVEL
                    ? 0 : enhancementCost(e, enhancementLevel));
        }
        return vo;
    }

    private Map<String, Object> enhancedStats(Equipment equipment, PlayerEquipment playerEquipment) {
        Map<String, Object> stats = parseJsonMap(equipment.getBaseStatsJson());
        int level = Math.max(0, playerEquipment.getEnhancementLevel() != null
                ? playerEquipment.getEnhancementLevel() : 0);
        if (level == 0) return stats;

        double multiplier = 1 + level * ENHANCEMENT_BONUS_PER_LEVEL;
        stats.replaceAll((key, value) -> value instanceof Number number
                ? Math.round(number.doubleValue() * multiplier * 10_000.0) / 10_000.0
                : value);
        return stats;
    }

    private int enhancementCost(Equipment equipment, int currentLevel) {
        long sellPrice = Math.max(0, equipment.getSellPrice() != null ? equipment.getSellPrice() : 0);
        long repairCost = Math.max(0, equipment.getRepairCost() != null ? equipment.getRepairCost() : 10);
        long baseCost = Math.max(20, (sellPrice + repairCost) / 2);
        long cost = baseCost * (Math.max(0, currentLevel) + 1L);
        return (int) Math.min(Integer.MAX_VALUE, cost);
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

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }
}
