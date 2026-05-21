package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("quest_progress_logs")
public class QuestProgressLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerId;
    private String questKey;
    private Integer delta;
    private Integer progressBefore;
    private Integer progressAfter;
    private String source;
    private String relatedId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getQuestKey() { return questKey; }
    public void setQuestKey(String questKey) { this.questKey = questKey; }
    public Integer getDelta() { return delta; }
    public void setDelta(Integer delta) { this.delta = delta; }
    public Integer getProgressBefore() { return progressBefore; }
    public void setProgressBefore(Integer progressBefore) { this.progressBefore = progressBefore; }
    public Integer getProgressAfter() { return progressAfter; }
    public void setProgressAfter(Integer progressAfter) { this.progressAfter = progressAfter; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
