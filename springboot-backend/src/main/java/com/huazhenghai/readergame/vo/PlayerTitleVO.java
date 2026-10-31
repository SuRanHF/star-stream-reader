package com.huazhenghai.readergame.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PlayerTitleVO {
    private String titleKey;
    private String name;
    private String category;
    private String rarity;
    private String description;
    private boolean equipped;
    private String source;
    private LocalDateTime unlockedAt;
    private Map<String, Number> effects;
    private List<String> inactiveEffects;
    private List<String> uncertainEffects;
    private String effectScope;

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
    public boolean isEquipped() { return equipped; }
    public void setEquipped(boolean equipped) { this.equipped = equipped; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }
    public Map<String, Number> getEffects() { return effects; }
    public void setEffects(Map<String, Number> effects) { this.effects = effects; }
    public List<String> getInactiveEffects() { return inactiveEffects; }
    public void setInactiveEffects(List<String> inactiveEffects) { this.inactiveEffects = inactiveEffects; }
    public List<String> getUncertainEffects() { return uncertainEffects; }
    public void setUncertainEffects(List<String> uncertainEffects) { this.uncertainEffects = uncertainEffects; }
    public String getEffectScope() { return effectScope; }
    public void setEffectScope(String effectScope) { this.effectScope = effectScope; }
}
