package com.huazhenghai.readergame.vo;

/** Public aggregate only; deliberately contains no story key, choice, or consequence. */
public class StoryDiscoveryRankingVO {

    private int rank;
    private Long playerId;
    private String playerName;
    private int discoveryCount;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getDiscoveryCount() { return discoveryCount; }
    public void setDiscoveryCount(int discoveryCount) { this.discoveryCount = discoveryCount; }
}
