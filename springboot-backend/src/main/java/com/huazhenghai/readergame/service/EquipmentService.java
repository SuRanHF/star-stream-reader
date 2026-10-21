package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;
import java.util.Map;

public interface EquipmentService {

    List<EquipmentVO> getAllEquipment();

    List<PlayerEquipmentVO> getPlayerEquipment(Long playerId, Long userId);

    Map<String, PlayerEquipmentVO> getEquippedSlots(Long playerId, Long userId);

    Map<String, Object> calculateEquipmentBonus(Long playerId);

    /** Sums finite numeric special effects from the player's equipped, durable equipment. */
    Map<String, Object> calculateEquipmentSpecialEffectBonuses(Long playerId);

    /** 返回每个 stat → "装备名+值, 装备名+值" 的详情字符串 */
    Map<String, String> getEquipmentBonusDetail(Long playerId);

    /** 获取套装定义及其组成装备 */
    List<Map<String, Object>> getEquipmentSets();

    /** 获取玩家当前已激活的套装和套装总加成 */
    Map<String, Object> getPlayerSetBonuses(Long playerId);

    int addEquipment(Long playerId, String equipmentKey, String source);

    /** Grants one equipment item and returns its catalog name for durable reward snapshots. */
    EquipmentGrantResult addEquipmentWithResult(Long playerId, String equipmentKey, String source);

    PlayerEquipmentVO equip(Long playerId, String equipmentKey, Long userId);

    PlayerEquipmentVO unequip(Long playerId, String equipmentKey, Long userId);

    Map<String, Object> repair(Long playerId, String equipmentKey, Long userId);

    Map<String, Object> enhance(Long playerId, String equipmentKey, Long userId, String requestKey);
}
