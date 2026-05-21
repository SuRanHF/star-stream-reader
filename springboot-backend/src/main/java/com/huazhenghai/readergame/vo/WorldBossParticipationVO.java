package com.huazhenghai.readergame.vo;

public class WorldBossParticipationVO {

    private Long id;
    private String bossNo;
    private Long playerId;
    private String playerName;
    private String partyNo;
    private Long damage;
    private int attackCount;
    private String lastAttackAt;
    private boolean rewardClaimed;
    private Integer rankNo;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getPartyNo() { return partyNo; }
    public void setPartyNo(String partyNo) { this.partyNo = partyNo; }
    public Long getDamage() { return damage; }
    public void setDamage(Long damage) { this.damage = damage; }
    public int getAttackCount() { return attackCount; }
    public void setAttackCount(int attackCount) { this.attackCount = attackCount; }
    public String getLastAttackAt() { return lastAttackAt; }
    public void setLastAttackAt(String lastAttackAt) { this.lastAttackAt = lastAttackAt; }
    public boolean isRewardClaimed() { return rewardClaimed; }
    public void setRewardClaimed(boolean rewardClaimed) { this.rewardClaimed = rewardClaimed; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
