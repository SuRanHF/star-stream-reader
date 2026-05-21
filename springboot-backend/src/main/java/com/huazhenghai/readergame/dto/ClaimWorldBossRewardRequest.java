package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;

public class ClaimWorldBossRewardRequest {

    @NotBlank(message = "bossNo 不能为空")
    private String bossNo;

    private Long playerId;

    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
}
