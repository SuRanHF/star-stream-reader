package com.huazhenghai.readergame.vo;

public class ExploreActionRankingVO {
    private int rank;
    private Long playerId;
    private String playerName;
    private int actionCount;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getActionCount() { return actionCount; }
    public void setActionCount(int actionCount) { this.actionCount = actionCount; }
}
