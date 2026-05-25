package com.huazhenghai.readergame.vo;

public class PlayerFactionVO {

    private Long id;
    private Long playerId;
    private String factionKey;
    private String factionName;
    private String role;
    private Long reputation;
    private Long contributionTotal;
    private String joinedAt;
    private String leftAt;
    private String status;

    // 前端兼容字段 (从 ConstellationFaction 填充)
    private Integer factionLevel;
    private Integer activeMembers;
    private Long totalContributionScore;
    private java.util.List<String> factionSkills;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getFactionKey() { return factionKey; }
    public void setFactionKey(String factionKey) { this.factionKey = factionKey; }
    @com.fasterxml.jackson.annotation.JsonProperty("constellationKey")
    public String getConstellationKey() { return factionKey; }
    public String getFactionName() { return factionName; }
    public void setFactionName(String factionName) { this.factionName = factionName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getReputation() { return reputation; }
    public void setReputation(Long reputation) { this.reputation = reputation; }
    public Long getContributionTotal() { return contributionTotal; }
    public void setContributionTotal(Long contributionTotal) { this.contributionTotal = contributionTotal; }
    @com.fasterxml.jackson.annotation.JsonProperty("totalContributionScore")
    public Long getTotalContributionScore() { return contributionTotal; }
    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }
    public String getLeftAt() { return leftAt; }
    public void setLeftAt(String leftAt) { this.leftAt = leftAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getFactionLevel() { return factionLevel; }
    public void setFactionLevel(Integer factionLevel) { this.factionLevel = factionLevel; }
    public Integer getActiveMembers() { return activeMembers; }
    public void setActiveMembers(Integer activeMembers) { this.activeMembers = activeMembers; }
    public void setTotalContributionScore(Long totalContributionScore) { this.totalContributionScore = totalContributionScore; }
    public java.util.List<String> getFactionSkills() { return factionSkills; }
    public void setFactionSkills(java.util.List<String> factionSkills) { this.factionSkills = factionSkills; }
}
