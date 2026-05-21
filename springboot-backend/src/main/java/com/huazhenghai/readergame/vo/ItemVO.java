package com.huazhenghai.readergame.vo;

import java.util.Map;

public class ItemVO {

    private String itemKey;
    private String name;
    private String itemType;
    private String rarity;
    private String description;
    private Map<String, Object> effects;
    private boolean consumeOnUse;
    private int sellPrice;
    private int maxStack;

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getEffects() { return effects; }
    public void setEffects(Map<String, Object> effects) { this.effects = effects; }
    public boolean isConsumeOnUse() { return consumeOnUse; }
    public void setConsumeOnUse(boolean consumeOnUse) { this.consumeOnUse = consumeOnUse; }
    public int getSellPrice() { return sellPrice; }
    public void setSellPrice(int sellPrice) { this.sellPrice = sellPrice; }
    public int getMaxStack() { return maxStack; }
    public void setMaxStack(int maxStack) { this.maxStack = maxStack; }
}
