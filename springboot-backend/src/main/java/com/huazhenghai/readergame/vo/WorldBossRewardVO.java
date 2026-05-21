package com.huazhenghai.readergame.vo;

import java.util.Map;

public class WorldBossRewardVO {

    private boolean claimed;
    private int rankNo;
    private Map<String, Object> rewards;
    private String message;

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public int getRankNo() { return rankNo; }
    public void setRankNo(int rankNo) { this.rankNo = rankNo; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
