package com.huazhenghai.readergame.vo;

import java.util.Map;

public class FactionSkillVO {

    private Long id;
    private String factionKey;
    private String skillKey;
    private String name;
    private String description;
    private Integer unlockLevel;
    private Map<String, Object> effects;
    private Map<String, Object> cost;
    private Integer enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFactionKey() { return factionKey; }
    public void setFactionKey(String factionKey) { this.factionKey = factionKey; }
    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getUnlockLevel() { return unlockLevel; }
    public void setUnlockLevel(Integer unlockLevel) { this.unlockLevel = unlockLevel; }
    public Map<String, Object> getEffects() { return effects; }
    public void setEffects(Map<String, Object> effects) { this.effects = effects; }
    public Map<String, Object> getCost() { return cost; }
    public void setCost(Map<String, Object> cost) { this.cost = cost; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
