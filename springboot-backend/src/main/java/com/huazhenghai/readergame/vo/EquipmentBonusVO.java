package com.huazhenghai.readergame.vo;

import java.util.Map;

public class EquipmentBonusVO {

    private Map<String, Object> bonus;
    private int equippedCount;
    private int ownedCount;

    public Map<String, Object> getBonus() { return bonus; }
    public void setBonus(Map<String, Object> bonus) { this.bonus = bonus; }
    public int getEquippedCount() { return equippedCount; }
    public void setEquippedCount(int equippedCount) { this.equippedCount = equippedCount; }
    public int getOwnedCount() { return ownedCount; }
    public void setOwnedCount(int ownedCount) { this.ownedCount = ownedCount; }
}
