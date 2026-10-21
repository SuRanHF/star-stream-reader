package com.huazhenghai.readergame.vo;

public class ExploreActionPositionVO {
    private boolean participated;
    private int rank;
    private int participantCount;
    private int actionCount;
    private Integer nextRankActionCount;
    private int actionsToNextRank;

    public boolean isParticipated() { return participated; }
    public void setParticipated(boolean participated) { this.participated = participated; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }
    public int getActionCount() { return actionCount; }
    public void setActionCount(int actionCount) { this.actionCount = actionCount; }
    public Integer getNextRankActionCount() { return nextRankActionCount; }
    public void setNextRankActionCount(Integer nextRankActionCount) { this.nextRankActionCount = nextRankActionCount; }
    public int getActionsToNextRank() { return actionsToNextRank; }
    public void setActionsToNextRank(int actionsToNextRank) { this.actionsToNextRank = actionsToNextRank; }
}
