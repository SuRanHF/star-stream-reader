package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("locations")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("location_key")
    private String locationKey;

    private String name;

    private String description;

    @TableField("unlock_conditions_json")
    private String unlockConditionsJson;

    @TableField("event_rates_json")
    private String eventRatesJson;

    @TableField("min_level")
    private Integer minLevel;

    @TableField("danger_level")
    private Integer dangerLevel;

    @TableField("recommended_rank")
    private String recommendedRank;

    @TableField("is_default")
    private Integer isDefault;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUnlockConditionsJson() { return unlockConditionsJson; }
    public void setUnlockConditionsJson(String unlockConditionsJson) { this.unlockConditionsJson = unlockConditionsJson; }
    public String getEventRatesJson() { return eventRatesJson; }
    public void setEventRatesJson(String eventRatesJson) { this.eventRatesJson = eventRatesJson; }
    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
    public Integer getDangerLevel() { return dangerLevel; }
    public void setDangerLevel(Integer dangerLevel) { this.dangerLevel = dangerLevel; }
    public String getRecommendedRank() { return recommendedRank; }
    public void setRecommendedRank(String recommendedRank) { this.recommendedRank = recommendedRank; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
