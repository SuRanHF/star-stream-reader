package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class BroadcastSummaryVO {

    private int activeCount;
    private int totalContributors;
    private List<Map<String, Object>> topEvents;

    public int getActiveCount() { return activeCount; }
    public void setActiveCount(int activeCount) { this.activeCount = activeCount; }
    public int getTotalContributors() { return totalContributors; }
    public void setTotalContributors(int totalContributors) { this.totalContributors = totalContributors; }
    public List<Map<String, Object>> getTopEvents() { return topEvents; }
    public void setTopEvents(List<Map<String, Object>> topEvents) { this.topEvents = topEvents; }
}
