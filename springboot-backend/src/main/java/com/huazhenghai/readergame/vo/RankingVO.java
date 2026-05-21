package com.huazhenghai.readergame.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RankingVO {

    private int rank;
    @JsonProperty("player_id")
    private Long playerId;
    @JsonProperty("player_name")
    private String playerName;
    private int level;
    private int rating;
    private int wins;
    private int losses;
    @JsonProperty("highest_rating")
    private int highestRating;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getHighestRating() { return highestRating; }
    public void setHighestRating(int highestRating) { this.highestRating = highestRating; }
}
