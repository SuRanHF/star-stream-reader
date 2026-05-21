package com.huazhenghai.readergame.vo;

import java.util.Map;

public class ExploreResultVO {

    private String resultType;
    private String staminaCost;
    private Map<String, Object> event;
    private Map<String, Object> rewards;
    private Map<String, Object> progressEffects;
    private Map<String, Object> player;
    private Map<String, Object> newLogs;

    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }
    public String getStaminaCost() { return staminaCost; }
    public void setStaminaCost(String staminaCost) { this.staminaCost = staminaCost; }
    public Map<String, Object> getEvent() { return event; }
    public void setEvent(Map<String, Object> event) { this.event = event; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }
    public Map<String, Object> getProgressEffects() { return progressEffects; }
    public void setProgressEffects(Map<String, Object> progressEffects) { this.progressEffects = progressEffects; }
    public Map<String, Object> getPlayer() { return player; }
    public void setPlayer(Map<String, Object> player) { this.player = player; }
    public Map<String, Object> getNewLogs() { return newLogs; }
    public void setNewLogs(Map<String, Object> newLogs) { this.newLogs = newLogs; }
}
