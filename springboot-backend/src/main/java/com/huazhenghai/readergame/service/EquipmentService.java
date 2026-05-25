package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;
import java.util.Map;

public interface EquipmentService {

    List<EquipmentVO> getAllEquipment();

    List<PlayerEquipmentVO> getPlayerEquipment(Long playerId, Long userId);

    Map<String, PlayerEquipmentVO> getEquippedSlots(Long playerId, Long userId);

    Map<String, Object> calculateEquipmentBonus(Long playerId);

    /** 返回每个 stat → "装备名+值, 装备名+值" 的详情字符串 */
    Map<String, String> getEquipmentBonusDetail(Long playerId);

    int addEquipment(Long playerId, String equipmentKey, String source);

    PlayerEquipmentVO equip(Long playerId, String equipmentKey, Long userId);

    PlayerEquipmentVO unequip(Long playerId, String equipmentKey, Long userId);

    Map<String, Object> repair(Long playerId, String equipmentKey, Long userId);
}
