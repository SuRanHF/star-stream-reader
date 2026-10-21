package com.huazhenghai.readergame.vo;

/** Personal aggregate position; contains no private story identifiers or choices. */
public class StoryDiscoveryPositionVO {

    private int rank;
    private int participantCount;
    private int discoveryCount;
    private Integer nextRankDiscoveryCount;
    private int discoveriesToNextRank;
    private boolean ranked;

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }

    public int getDiscoveryCount() { return discoveryCount; }
    public void setDiscoveryCount(int discoveryCount) { this.discoveryCount = discoveryCount; }

    public Integer getNextRankDiscoveryCount() { return nextRankDiscoveryCount; }
    public void setNextRankDiscoveryCount(Integer nextRankDiscoveryCount) {
        this.nextRankDiscoveryCount = nextRankDiscoveryCount;
    }

    public int getDiscoveriesToNextRank() { return discoveriesToNextRank; }
    public void setDiscoveriesToNextRank(int discoveriesToNextRank) {
        this.discoveriesToNextRank = discoveriesToNextRank;
    }

    public boolean isRanked() { return ranked; }
    public void setRanked(boolean ranked) { this.ranked = ranked; }
}
