package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClaimQuestRewardRequest {

    private Long playerId;
    private String questKey;
    private String cycleKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getQuestKey() { return questKey; }
    public void setQuestKey(String questKey) { this.questKey = questKey; }
    public String getCycleKey() { return cycleKey; }
    public void setCycleKey(String cycleKey) { this.cycleKey = cycleKey; }

    /** 兼容前端 "questId" 字段 — questKey/cycleKey 由 service 从 player_quest 表解析 */
    @JsonProperty("questId")
    public void setQuestId(Object questId) {
        if (this.questKey == null && questId != null) {
            this.questKey = String.valueOf(questId);
        }
    }
}
