package com.huazhenghai.readergame.vo;

public class PkPerformancePositionVO {
    private boolean participated;
    private int rank;
    private int participantCount;
    private int ratingDelta;
    private int matchCount;
    private int winCount;
    private Integer nextRankRatingDelta;
    private int pointsToNextRank;

    public boolean isParticipated() { return participated; }
    public void setParticipated(boolean participated) { this.participated = participated; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }
    public int getRatingDelta() { return ratingDelta; }
    public void setRatingDelta(int ratingDelta) { this.ratingDelta = ratingDelta; }
    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }
    public int getWinCount() { return winCount; }
    public void setWinCount(int winCount) { this.winCount = winCount; }
    public Integer getNextRankRatingDelta() { return nextRankRatingDelta; }
    public void setNextRankRatingDelta(Integer nextRankRatingDelta) { this.nextRankRatingDelta = nextRankRatingDelta; }
    public int getPointsToNextRank() { return pointsToNextRank; }
    public void setPointsToNextRank(int pointsToNextRank) { this.pointsToNextRank = pointsToNextRank; }
}
