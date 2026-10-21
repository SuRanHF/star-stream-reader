package com.huazhenghai.readergame.vo;

public class PkPerformanceRankingVO {
    private int rank;
    private Long playerId;
    private String playerName;
    private int ratingDelta;
    private int matchCount;
    private int winCount;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getRatingDelta() { return ratingDelta; }
    public void setRatingDelta(int ratingDelta) { this.ratingDelta = ratingDelta; }
    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }
    public int getWinCount() { return winCount; }
    public void setWinCount(int winCount) { this.winCount = winCount; }
}
