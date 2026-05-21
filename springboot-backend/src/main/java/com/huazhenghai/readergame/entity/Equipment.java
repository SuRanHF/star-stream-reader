package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("equipment")
public class Equipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("equipment_key")
    private String equipmentKey;

    private String name;

    private String slot;

    private String rarity;

    private String description;

    @TableField("base_stats_json")
    private String baseStatsJson;

    @TableField("special_effects_json")
    private String specialEffectsJson;

    @TableField("set_key")
    private String setKey;

    @TableField("max_durability")
    private Integer maxDurability;

    @TableField("repair_cost")
    private Integer repairCost;

    @TableField("sell_price")
    private Integer sellPrice;

    private Integer enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getBaseStatsJson() { return baseStatsJson; }
    public void setBaseStatsJson(String baseStatsJson) { this.baseStatsJson = baseStatsJson; }
    public String getSpecialEffectsJson() { return specialEffectsJson; }
    public void setSpecialEffectsJson(String specialEffectsJson) { this.specialEffectsJson = specialEffectsJson; }
    public String getSetKey() { return setKey; }
    public void setSetKey(String setKey) { this.setKey = setKey; }
    public Integer getMaxDurability() { return maxDurability; }
    public void setMaxDurability(Integer maxDurability) { this.maxDurability = maxDurability; }
    public Integer getRepairCost() { return repairCost; }
    public void setRepairCost(Integer repairCost) { this.repairCost = repairCost; }
    public Integer getSellPrice() { return sellPrice; }
    public void setSellPrice(Integer sellPrice) { this.sellPrice = sellPrice; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
