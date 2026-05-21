package com.huazhenghai.readergame.vo;

import java.util.Map;

/**
 * 技能定义视图对象.
 */
public class SkillVO {

    private String skillKey;
    private String name;
    private String type;
    private String rarity;
    private String description;
    private Map<String, Object> unlockConditions;
    private Map<String, Object> effects;
    private Map<String, Object> cost;
    private Integer cooldownSeconds;
    private boolean unlocked;
    private boolean unlockable;

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
    public Map<String, Object> getUnlockConditions() { return unlockConditions; }
    public void setUnlockConditions(Map<String, Object> unlockConditions) { this.unlockConditions = unlockConditions; }
    public Map<String, Object> getEffects() { return effects; }
    public void setEffects(Map<String, Object> effects) { this.effects = effects; }
    public Map<String, Object> getCost() { return cost; }
    public void setCost(Map<String, Object> cost) { this.cost = cost; }
    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    public boolean isUnlockable() { return unlockable; }
    public void setUnlockable(boolean unlockable) { this.unlockable = unlockable; }
}
