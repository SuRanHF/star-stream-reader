package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("titles")
public class Title {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("title_key")
    private String titleKey;

    private String name;
    private String category;
    private String rarity;
    private String description;

    @TableField("unlock_conditions_json")
    private String unlockConditionsJson;

    @TableField("effects_json")
    private String effectsJson;

    @TableField("tags_json")
    private String tagsJson;

    @TableField("strong_against_json")
    private String strongAgainstJson;

    @TableField("weak_against_json")
    private String weakAgainstJson;

    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitleKey() { return titleKey; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUnlockConditionsJson() { return unlockConditionsJson; }
    public void setUnlockConditionsJson(String unlockConditionsJson) { this.unlockConditionsJson = unlockConditionsJson; }
    public String getEffectsJson() { return effectsJson; }
    public void setEffectsJson(String effectsJson) { this.effectsJson = effectsJson; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getStrongAgainstJson() { return strongAgainstJson; }
    public void setStrongAgainstJson(String strongAgainstJson) { this.strongAgainstJson = strongAgainstJson; }
    public String getWeakAgainstJson() { return weakAgainstJson; }
    public void setWeakAgainstJson(String weakAgainstJson) { this.weakAgainstJson = weakAgainstJson; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
