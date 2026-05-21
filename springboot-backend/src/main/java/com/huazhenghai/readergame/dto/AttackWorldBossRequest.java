package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AttackWorldBossRequest {

    private String bossNo;
    private Long playerId;

    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    /** 兼容前端 "action" 字段 — bossNo 由 active boss 推导 */
    @JsonProperty("action")
    public void setAction(String action) { /* bossNo derived from active boss in service */ }
}
