package com.huazhenghai.readergame.vo;

public class WorldBossRankingVO {

    private int rank;
    private Long playerId;
    private String playerName;
    private Long damage;
    private int attackCount;
    private boolean rewardClaimed;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Long getDamage() { return damage; }
    public void setDamage(Long damage) { this.damage = damage; }
    public int getAttackCount() { return attackCount; }
    public void setAttackCount(int attackCount) { this.attackCount = attackCount; }
    public boolean isRewardClaimed() { return rewardClaimed; }
    public void setRewardClaimed(boolean rewardClaimed) { this.rewardClaimed = rewardClaimed; }
}
