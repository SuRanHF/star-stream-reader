package com.huazhenghai.readergame.vo;

public class SellItemResultVO {

    private String itemKey;
    private String itemName;
    private int sellQuantity;
    private int sellPrice;
    private int totalCoinsGained;
    private int remainingQuantity;

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getSellQuantity() { return sellQuantity; }
    public void setSellQuantity(int sellQuantity) { this.sellQuantity = sellQuantity; }
    public int getSellPrice() { return sellPrice; }
    public void setSellPrice(int sellPrice) { this.sellPrice = sellPrice; }
    public int getTotalCoinsGained() { return totalCoinsGained; }
    public void setTotalCoinsGained(int totalCoinsGained) { this.totalCoinsGained = totalCoinsGained; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(int remainingQuantity) { this.remainingQuantity = remainingQuantity; }
}
