package com.huazhenghai.readergame.vo;

import java.util.Map;

public class UseItemResultVO {

    private String itemKey;
    private String itemName;
    private int remainingQuantity;
    private Map<String, Object> appliedEffects;

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(int remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public Map<String, Object> getAppliedEffects() { return appliedEffects; }
    public void setAppliedEffects(Map<String, Object> appliedEffects) { this.appliedEffects = appliedEffects; }
}
