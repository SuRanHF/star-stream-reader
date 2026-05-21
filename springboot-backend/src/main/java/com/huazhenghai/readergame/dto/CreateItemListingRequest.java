package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateItemListingRequest {

    private Long sellerPlayerId;
    private String itemKey;
    private int quantity = 1;
    private int unitPrice;

    public Long getSellerPlayerId() { return sellerPlayerId; }
    public void setSellerPlayerId(Long sellerPlayerId) { this.sellerPlayerId = sellerPlayerId; }

    /** 兼容前端 "sellerId" 字段 */
    @JsonProperty("sellerId")
    public void setSellerId(Long sellerId) {
        if (this.sellerPlayerId == null) this.sellerPlayerId = sellerId;
    }

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }

    /** 兼容前端 "price" 字段 */
    @JsonProperty("price")
    public void setPrice(int price) {
        this.unitPrice = price;
    }

    /** 兼容前端 "itemType" 字段 (ignored, 路由已区分 item/equipment) */
    @JsonProperty("itemType")
    public void setItemType(String itemType) { /* ignored */ }
}
