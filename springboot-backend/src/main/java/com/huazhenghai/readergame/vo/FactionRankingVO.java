package com.huazhenghai.readergame.vo;

public class FactionRankingVO {

    private Integer rank;
    private String factionKey;
    private String factionName;
    private String alignment;
    private Integer level;
    private Integer memberCount;
    private Long totalContribution;

    // for player contribution rankings
    private Long playerId;
    private String playerName;
    private Long contributionValue;

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public String getFactionKey() { return factionKey; }
    public void setFactionKey(String factionKey) { this.factionKey = factionKey; }
    public String getFactionName() { return factionName; }
    public void setFactionName(String factionName) { this.factionName = factionName; }
    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Long getTotalContribution() { return totalContribution; }
    public void setTotalContribution(Long totalContribution) { this.totalContribution = totalContribution; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Long getContributionValue() { return contributionValue; }
    public void setContributionValue(Long contributionValue) { this.contributionValue = contributionValue; }
}
