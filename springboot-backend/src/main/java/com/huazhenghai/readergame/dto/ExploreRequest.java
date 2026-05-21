package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class ExploreRequest {

    @NotNull(message = "玩家ID不能为空")
    private Long playerId;

    private String locationKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }
}
