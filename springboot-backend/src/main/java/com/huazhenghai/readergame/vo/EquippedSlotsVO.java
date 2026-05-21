package com.huazhenghai.readergame.vo;

import java.util.Map;

public class EquippedSlotsVO {

    private Map<String, PlayerEquipmentVO> slots;
    private Map<String, Object> totalBonus;

    public Map<String, PlayerEquipmentVO> getSlots() { return slots; }
    public void setSlots(Map<String, PlayerEquipmentVO> slots) { this.slots = slots; }
    public Map<String, Object> getTotalBonus() { return totalBonus; }
    public void setTotalBonus(Map<String, Object> totalBonus) { this.totalBonus = totalBonus; }
}
