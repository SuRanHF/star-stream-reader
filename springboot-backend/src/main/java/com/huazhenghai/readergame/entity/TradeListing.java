package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("trade_listings")
public class TradeListing {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("listing_no")
    private String listingNo;

    @TableField("seller_player_id")
    private Long sellerPlayerId;

    @TableField("listing_type")
    private String listingType;

    @TableField("item_key")
    private String itemKey;

    @TableField("equipment_key")
    private String equipmentKey;

    private Integer quantity;

    @TableField("unit_price")
    private Integer unitPrice;

    @TableField("total_price")
    private Integer totalPrice;

    private String status;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("sold_at")
    private LocalDateTime soldAt;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getListingNo() { return listingNo; }
    public void setListingNo(String listingNo) { this.listingNo = listingNo; }
    public Long getSellerPlayerId() { return sellerPlayerId; }
    public void setSellerPlayerId(Long sellerPlayerId) { this.sellerPlayerId = sellerPlayerId; }
    public String getListingType() { return listingType; }
    public void setListingType(String listingType) { this.listingType = listingType; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getEquipmentKey() { return equipmentKey; }
    public void setEquipmentKey(String equipmentKey) { this.equipmentKey = equipmentKey; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Integer unitPrice) { this.unitPrice = unitPrice; }
    public Integer getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Integer totalPrice) { this.totalPrice = totalPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getSoldAt() { return soldAt; }
    public void setSoldAt(LocalDateTime soldAt) { this.soldAt = soldAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
}
