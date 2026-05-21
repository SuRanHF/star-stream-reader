package com.huazhenghai.readergame.vo;

public class FactionSummaryVO {

    private boolean joined;
    private String factionKey;
    private String factionName;
    private String role;
    private Long reputation;
    private Long contributionTotal;
    private Integer factionLevel;
    private Integer factionRank;

    public boolean isJoined() { return joined; }
    public void setJoined(boolean joined) { this.joined = joined; }
    public String getFactionKey() { return factionKey; }
    public void setFactionKey(String factionKey) { this.factionKey = factionKey; }
    public String getFactionName() { return factionName; }
    public void setFactionName(String factionName) { this.factionName = factionName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getReputation() { return reputation; }
    public void setReputation(Long reputation) { this.reputation = reputation; }
    public Long getContributionTotal() { return contributionTotal; }
    public void setContributionTotal(Long contributionTotal) { this.contributionTotal = contributionTotal; }
    public Integer getFactionLevel() { return factionLevel; }
    public void setFactionLevel(Integer factionLevel) { this.factionLevel = factionLevel; }
    public Integer getFactionRank() { return factionRank; }
    public void setFactionRank(Integer factionRank) { this.factionRank = factionRank; }
}
