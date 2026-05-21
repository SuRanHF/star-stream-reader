package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 创建队伍请求。前端发 leaderId + bossKey，后端兼容 playerId + name。
 */
public class CreatePartyRequest {

    private Long playerId;
    private String name;
    private String description;
    private String bossKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    /** 兼容前端 "leaderId" 字段 */
    @JsonProperty("leaderId")
    public void setLeaderId(Long leaderId) {
        if (this.playerId == null) this.playerId = leaderId;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** 兼容前端 bossKey，如果没有 name 则用 bossKey 生成 */
    @JsonProperty("bossKey")
    public void setBossKey(String bossKey) {
        this.bossKey = bossKey;
        if (this.name == null && bossKey != null && !bossKey.isBlank()) {
            this.name = "讨伐" + bossKey + "队伍";
        }
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
