package com.huazhenghai.readergame.vo;

import java.util.Map;

public class FactionVO {

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
    private Map<String, Object> buffs;
    private Map<String, Object> unlocks;
    private Map<String, Object> metadata;
    private String createdAt;
    private String updatedAt;

    // player-specific
    private Boolean joined;
    private Long myContribution;

    // top contributors
    private java.util.List<FactionRankingVO> topContributors;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFactionKey() { return factionKey; }
    public void setFactionKey(String factionKey) { this.factionKey = factionKey; }
    @com.fasterxml.jackson.annotation.JsonProperty("constellationKey")
    public String getConstellationKey() { return factionKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @com.fasterxml.jackson.annotation.JsonProperty("factionName")
    public String getFactionName() { return name; }
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
    @com.fasterxml.jackson.annotation.JsonProperty("factionLevel")
    public Integer getFactionLevel() { return level; }
    public Long getExp() { return exp; }
    public void setExp(Long exp) { this.exp = exp; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    @com.fasterxml.jackson.annotation.JsonProperty("activeMembers")
    public Integer getActiveMembers() { return memberCount; }
    public Long getTotalContribution() { return totalContribution; }
    public void setTotalContribution(Long totalContribution) { this.totalContribution = totalContribution; }
    @com.fasterxml.jackson.annotation.JsonProperty("totalContributionScore")
    public Long getTotalContributionScore() { return totalContribution; }
    public Map<String, Object> getBuffs() { return buffs; }
    public void setBuffs(Map<String, Object> buffs) { this.buffs = buffs; }
    public Map<String, Object> getUnlocks() { return unlocks; }
    public void setUnlocks(Map<String, Object> unlocks) { this.unlocks = unlocks; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getJoined() { return joined; }
    public void setJoined(Boolean joined) { this.joined = joined; }
    public Long getMyContribution() { return myContribution; }
    public void setMyContribution(Long myContribution) { this.myContribution = myContribution; }
    public java.util.List<FactionRankingVO> getTopContributors() { return topContributors; }
    public void setTopContributors(java.util.List<FactionRankingVO> topContributors) { this.topContributors = topContributors; }
}
