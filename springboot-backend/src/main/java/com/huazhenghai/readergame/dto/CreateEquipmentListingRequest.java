package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateEquipmentListingRequest {

    private Long sellerPlayerId;
    private String equipmentKey;
    private int unitPrice;

    public Long getSellerPlayerId() { return sellerPlayerId; }
    public void setSellerPlayerId(Long sellerPlayerId) { this.sellerPlayerId = sellerPlayerId; }

    /** 兼容前端 "sellerId" 字段 */
    @JsonProperty("sellerId")
    public void setSellerId(Long sellerId) {
        if (this.sellerPlayerId == null) this.sellerPlayerId = sellerId;
    }

    public String getEquipmentKey() { return equipmentKey; }
    public void setEquipmentKey(String equipmentKey) { this.equipmentKey = equipmentKey; }

    /** 兼容前端 "itemKey" 字段 (前端统一用 itemKey) */
    @JsonProperty("itemKey")
    public void setItemKeyAlias(String itemKey) {
        if (this.equipmentKey == null) this.equipmentKey = itemKey;
    }

    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }

    /** 兼容前端 "price" 字段 */
    @JsonProperty("price")
    public void setPrice(int price) {
        this.unitPrice = price;
    }

    /** 兼容前端 "quantity" 字段 (装备上架无需 quantity) */
    @JsonProperty("quantity")
    public void setQuantityCompat(int quantity) { /* ignored */ }

    /** 兼容前端 "itemType" 字段 */
    @JsonProperty("itemType")
    public void setItemType(String itemType) { /* ignored */ }
}
