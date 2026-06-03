package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class MonsterVO {

    private String monsterKey;
    private String name;
    private String type;
    private String rarity;
    private String locationKey;
    private String description;
    private Map<String, Object> stats;
    private Map<String, Object> skills;
    private Map<String, Object> rewards;
    private Map<String, Object> drops;
    private List<String> narrativeTags;
    private boolean enabled;

    public String getMonsterKey() { return monsterKey; }
    public void setMonsterKey(String monsterKey) { this.monsterKey = monsterKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getStats() { return stats; }
    public void setStats(Map<String, Object> stats) { this.stats = stats; }

    public Map<String, Object> getSkills() { return skills; }
    public void setSkills(Map<String, Object> skills) { this.skills = skills; }

    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }

    public Map<String, Object> getDrops() { return drops; }
    public void setDrops(Map<String, Object> drops) { this.drops = drops; }

    public List<String> getNarrativeTags() { return narrativeTags; }
    public void setNarrativeTags(List<String> narrativeTags) { this.narrativeTags = narrativeTags; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
