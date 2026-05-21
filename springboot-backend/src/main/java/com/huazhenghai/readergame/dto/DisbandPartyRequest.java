package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class DisbandPartyRequest {

    @NotNull(message = "leaderPlayerId 不能为空")
    private Long leaderPlayerId;

    public Long getLeaderPlayerId() { return leaderPlayerId; }
    public void setLeaderPlayerId(Long leaderPlayerId) { this.leaderPlayerId = leaderPlayerId; }
}
