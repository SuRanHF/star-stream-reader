package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClaimWorldBossRewardRequest {

    @NotBlank(message = "bossNo 不能为空")
    private String bossNo;

    private Long playerId;

    @NotBlank(message = "requestKey 不能为空")
    @Size(max = 64, message = "requestKey 长度不能超过 64")
    private String requestKey;

    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
