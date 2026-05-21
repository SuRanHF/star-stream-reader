package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Equipment;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerEquipment;
import com.huazhenghai.readergame.mapper.EquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerEquipmentMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.vo.EquipmentVO;
import com.huazhenghai.readergame.vo.PlayerEquipmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EquipmentServiceImpl implements EquipmentService {

    private final PlayerMapper playerMapper;
    private final EquipmentMapper equipmentMapper;
    private final PlayerEquipmentMapper playerEquipmentMapper;
    private final PlayerLogService playerLogService;
    private final ObjectMapper objectMapper;

    public EquipmentServiceImpl(PlayerMapper playerMapper,
                                EquipmentMapper equipmentMapper,
                                PlayerEquipmentMapper playerEquipmentMapper,
                                PlayerLogService playerLogService,
                                ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.equipmentMapper = equipmentMapper;
        this.playerEquipmentMapper = playerEquipmentMapper;
        this.playerLogService = playerLogService;
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

            Map<String, Object> stats = parseJsonMap(equip.getBaseStatsJson());
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
    @Transactional
    public int addEquipment(Long playerId, String equipmentKey, String source) {
        QueryWrapper<Equipment> eqQuery = new QueryWrapper<>();
        eqQuery.eq("equipment_key", equipmentKey).eq("enabled", 1);
        Equipment equip = equipmentMapper.selectOne(eqQuery);
        if (equip == null) return 0;

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        PlayerEquipment existing = playerEquipmentMapper.selectOne(peQuery);
        if (existing != null) return 0;

        PlayerEquipment pe = new PlayerEquipment();
        pe.setPlayerId(playerId);
        pe.setEquipmentKey(equipmentKey);
        pe.setSlot(equip.getSlot());
        pe.setEquipped(0);
        pe.setDurability(equip.getMaxDurability() != null ? equip.getMaxDurability() : 100);
        pe.setEnhancementLevel(0);
        playerEquipmentMapper.insert(pe);

        if (source != null && !source.isBlank()) {
            playerLogService.addLog(playerId, "equipment_acquire", "获得装备: " + equip.getName(),
                    Map.of("equipmentKey", equipmentKey, "source", source));
        }
        return 1;
    }

    @Override
    @Transactional
    public PlayerEquipmentVO equip(Long playerId, String equipmentKey, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

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
            playerEquipmentMapper.updateById(oldEquipped);
        }

        // 穿戴目标装备
        target.setEquipped(1);
        playerEquipmentMapper.updateById(target);

        playerLogService.addLog(playerId, "equipment_equip", "穿上了 " + equip.getName());

        return toPlayerEquipmentVO(target, equip);
    }

    @Override
    @Transactional
    public PlayerEquipmentVO unequip(Long playerId, String equipmentKey, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        QueryWrapper<PlayerEquipment> peQuery = new QueryWrapper<>();
        peQuery.eq("player_id", playerId).eq("equipment_key", equipmentKey);
        PlayerEquipment target = playerEquipmentMapper.selectOne(peQuery);
        if (target == null)
            throw new BusinessException(ErrorCode.EQUIP_NOT_OWNED, "未拥有此装备: " + equipmentKey);

        if (target.getEquipped() == null || target.getEquipped() != 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "装备未穿戴");

        target.setEquipped(0);
        playerEquipmentMapper.updateById(target);

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
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

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
            throw new BusinessException(ErrorCode.ITEM_NOT_ENOUGH, "金币不足，修理需要 " + repairCost + " 金币");

        player.setCoins(player.getCoins() - repairCost);
        playerMapper.updateById(player);

        target.setDurability(maxDura);
        playerEquipmentMapper.updateById(target);

        playerLogService.addLog(playerId, "equipment_repair",
                "修理了 " + equip.getName() + "（花费 " + repairCost + " 金币）",
                Map.of("equipmentKey", equipmentKey, "cost", repairCost));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipmentKey", equipmentKey);
        result.put("name", equip.getName());
        result.put("durability", maxDura);
        result.put("maxDurability", maxDura);
        result.put("repairCost", repairCost);
        result.put("remainingCoins", player.getCoins());
        return result;
    }

    // ─── 映射方法 ───

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
            vo.setBaseStats(parseJsonMap(e.getBaseStatsJson()));
            vo.setSpecialEffects(parseJsonMap(e.getSpecialEffectsJson()));
            vo.setSetKey(e.getSetKey());
            vo.setMaxDurability(e.getMaxDurability() != null ? e.getMaxDurability() : 100);
            vo.setRepairCost(e.getRepairCost() != null ? e.getRepairCost() : 10);
            vo.setSellPrice(e.getSellPrice() != null ? e.getSellPrice() : 0);
        }
        return vo;
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
