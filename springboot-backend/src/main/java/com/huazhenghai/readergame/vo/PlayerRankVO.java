package com.huazhenghai.readergame.vo;

public class PlayerRankVO {

    private int rank;
    private int totalPlayers;
    private int rating;
    private int wins;
    private int losses;
    private int highestRating;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getHighestRating() { return highestRating; }
    public void setHighestRating(int highestRating) { this.highestRating = highestRating; }
}
