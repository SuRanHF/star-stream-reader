package com.huazhenghai.readergame.vo;

import java.util.List;

/** Public, privacy-minimized summary of a concluded shared broadcast event. */
public class BroadcastHistoryVO {
    private String eventKey;
    private String title;
    private String description;
    private String type;
    /** completed when the collective target was reached; expired when its time window elapsed. */
    private String status;
    private Integer targetValue;
    private Integer currentValue;
    private Integer progressPercent;
    private String startAt;
    private String endAt;
    private String endedAt;
    private List<String> worldlineEffectSummaries;

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
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public String getEndedAt() { return endedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }
    public List<String> getWorldlineEffectSummaries() { return worldlineEffectSummaries; }
    public void setWorldlineEffectSummaries(List<String> worldlineEffectSummaries) {
        this.worldlineEffectSummaries = worldlineEffectSummaries;
    }
}
