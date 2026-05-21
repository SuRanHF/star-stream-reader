package com.huazhenghai.readergame.vo;

import java.util.Map;

/**
 * 玩家技能视图对象.
 */
public class PlayerSkillVO {

    private String skillKey;
    private String name;
    private String type;
    private String rarity;
    private String description;
    private Map<String, Object> effects;
    private Integer cooldownSeconds;
    private Integer level;
    private Integer equipped;
    private String unlockedAt;

    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getEffects() { return effects; }
    public void setEffects(Map<String, Object> effects) { this.effects = effects; }
    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getEquipped() { return equipped; }
    public void setEquipped(Integer equipped) { this.equipped = equipped; }
    public String getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(String unlockedAt) { this.unlockedAt = unlockedAt; }
}
