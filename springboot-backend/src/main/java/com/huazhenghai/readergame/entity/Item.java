package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("items")
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("item_key")
    private String itemKey;

    private String name;

    @TableField("item_type")
    private String itemType;

    private String rarity;

    private String description;

    @TableField("effects_json")
    private String effectsJson;

    @TableField("consume_on_use")
    private Integer consumeOnUse;

    @TableField("sell_price")
    private Integer sellPrice;

    @TableField("max_stack")
    private Integer maxStack;

    private Integer enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getEffectsJson() { return effectsJson; }
    public void setEffectsJson(String effectsJson) { this.effectsJson = effectsJson; }
    public Integer getConsumeOnUse() { return consumeOnUse; }
    public void setConsumeOnUse(Integer consumeOnUse) { this.consumeOnUse = consumeOnUse; }
    public Integer getSellPrice() { return sellPrice; }
    public void setSellPrice(Integer sellPrice) { this.sellPrice = sellPrice; }
    public Integer getMaxStack() { return maxStack; }
    public void setMaxStack(Integer maxStack) { this.maxStack = maxStack; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
