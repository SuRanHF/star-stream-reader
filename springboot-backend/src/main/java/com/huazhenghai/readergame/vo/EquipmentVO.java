package com.huazhenghai.readergame.vo;

import java.util.Map;

public class EquipmentVO {

    private String equipmentKey;
    private String name;
    private String slot;
    private String rarity;
    private String description;
    private Map<String, Object> baseStats;
    private Map<String, Object> specialEffects;
    private String setKey;
    private int maxDurability;
    private int repairCost;
    private int sellPrice;

    public String getEquipmentKey() { return equipmentKey; }
    public void setEquipmentKey(String equipmentKey) { this.equipmentKey = equipmentKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getBaseStats() { return baseStats; }
    public void setBaseStats(Map<String, Object> baseStats) { this.baseStats = baseStats; }
    public Map<String, Object> getSpecialEffects() { return specialEffects; }
    public void setSpecialEffects(Map<String, Object> specialEffects) { this.specialEffects = specialEffects; }
    public String getSetKey() { return setKey; }
    public void setSetKey(String setKey) { this.setKey = setKey; }
    public int getMaxDurability() { return maxDurability; }
    public void setMaxDurability(int maxDurability) { this.maxDurability = maxDurability; }
    public int getRepairCost() { return repairCost; }
    public void setRepairCost(int repairCost) { this.repairCost = repairCost; }
    public int getSellPrice() { return sellPrice; }
    public void setSellPrice(int sellPrice) { this.sellPrice = sellPrice; }
}
