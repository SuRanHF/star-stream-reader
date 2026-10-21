package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class ClaimQuestRewardRequest {

    private Long playerId;
    private String questKey;
    private String cycleKey;

    @Size(max = 64, message = "requestKey长度不能超过64")
    private String requestKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getQuestKey() { return questKey; }
    public void setQuestKey(String questKey) { this.questKey = questKey; }
    public String getCycleKey() { return cycleKey; }
    public void setCycleKey(String cycleKey) { this.cycleKey = cycleKey; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }

    /** 兼容前端 "questId" 字段 — questKey/cycleKey 由 service 从 player_quest 表解析 */
    @JsonProperty("questId")
    public void setQuestId(Object questId) {
        if (this.questKey == null && questId != null) {
            this.questKey = String.valueOf(questId);
        }
    }
}
