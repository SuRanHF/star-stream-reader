package com.huazhenghai.readergame.vo;

import java.util.Map;

public class BroadcastEventVO {

    private String eventKey;
    private String title;
    private String description;
    private String type;
    private String status;
    private Integer targetValue;
    private Integer currentValue;
    private String startAt;
    private String endAt;
    private Map<String, Object> rewards;
    private Map<String, Object> personalRewards;
    private Map<String, Object> worldlineEffects;
    private Map<String, Object> conditions;
    private Map<String, Object> metadata;
    private String createdBy;
    private String createdAt;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTargetValue() { return targetValue; }
    public void setTargetValue(Integer targetValue) { this.targetValue = targetValue; }
    public Integer getCurrentValue() { return currentValue; }
    public void setCurrentValue(Integer currentValue) { this.currentValue = currentValue; }
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }
    public Map<String, Object> getPersonalRewards() { return personalRewards; }
    public void setPersonalRewards(Map<String, Object> personalRewards) { this.personalRewards = personalRewards; }
    public Map<String, Object> getWorldlineEffects() { return worldlineEffects; }
    public void setWorldlineEffects(Map<String, Object> worldlineEffects) { this.worldlineEffects = worldlineEffects; }
    public Map<String, Object> getConditions() { return conditions; }
    public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
