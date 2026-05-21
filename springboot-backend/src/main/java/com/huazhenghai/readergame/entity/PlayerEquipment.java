package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("player_equipment")
public class PlayerEquipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("player_id")
    private Long playerId;

    @TableField("equipment_key")
    private String equipmentKey;

    private Integer equipped;

    private String slot;

    private Integer durability;

    @TableField("enhancement_level")
    private Integer enhancementLevel;

    private Integer listed;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("acquired_at")
    private LocalDateTime acquiredAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getEquipmentKey() { return equipmentKey; }
    public void setEquipmentKey(String equipmentKey) { this.equipmentKey = equipmentKey; }
    public Integer getEquipped() { return equipped; }
    public void setEquipped(Integer equipped) { this.equipped = equipped; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public Integer getDurability() { return durability; }
    public void setDurability(Integer durability) { this.durability = durability; }
    public Integer getEnhancementLevel() { return enhancementLevel; }
    public void setEnhancementLevel(Integer enhancementLevel) { this.enhancementLevel = enhancementLevel; }
    public Integer getListed() { return listed; }
    public void setListed(Integer listed) { this.listed = listed; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getAcquiredAt() { return acquiredAt; }
    public void setAcquiredAt(LocalDateTime acquiredAt) { this.acquiredAt = acquiredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
