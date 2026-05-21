package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("constellation_factions")
public class ConstellationFaction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String factionKey;
    private String name;
    private String constellationName;
    private String description;
    private String alignment;
    private String ideology;
    private Integer level;
    private Long exp;
    private Integer memberCount;
    private Long totalContribution;
    private String buffsJson;
    private String unlocksJson;
    private String metadataJson;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFactionKey() { return factionKey; }
    public void setFactionKey(String factionKey) { this.factionKey = factionKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getConstellationName() { return constellationName; }
    public void setConstellationName(String constellationName) { this.constellationName = constellationName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
    public String getIdeology() { return ideology; }
    public void setIdeology(String ideology) { this.ideology = ideology; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Long getExp() { return exp; }
    public void setExp(Long exp) { this.exp = exp; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Long getTotalContribution() { return totalContribution; }
    public void setTotalContribution(Long totalContribution) { this.totalContribution = totalContribution; }
    public String getBuffsJson() { return buffsJson; }
    public void setBuffsJson(String buffsJson) { this.buffsJson = buffsJson; }
    public String getUnlocksJson() { return unlocksJson; }
    public void setUnlocksJson(String unlocksJson) { this.unlocksJson = unlocksJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
