package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class RefreshQuestRequest {
    @NotNull private Long playerId;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
}
