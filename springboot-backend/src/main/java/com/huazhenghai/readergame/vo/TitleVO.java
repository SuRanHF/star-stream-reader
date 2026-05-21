package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class TitleVO {
    private String titleKey;
    private String name;
    private String category;
    private String rarity;
    private String description;
    private Map<String, Object> effects;

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
    public Map<String, Object> getEffects() { return effects; }
    public void setEffects(Map<String, Object> effects) { this.effects = effects; }
}
