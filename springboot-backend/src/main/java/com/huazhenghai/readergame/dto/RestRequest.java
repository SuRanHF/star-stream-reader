package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class RestRequest {

    @NotNull(message = "玩家ID不能为空")
    private Long playerId;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
}
