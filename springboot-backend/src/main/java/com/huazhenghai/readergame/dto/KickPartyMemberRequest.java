package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class KickPartyMemberRequest {

    @NotNull(message = "leaderPlayerId 不能为空")
    private Long leaderPlayerId;

    @NotNull(message = "targetPlayerId 不能为空")
    private Long targetPlayerId;

    public Long getLeaderPlayerId() { return leaderPlayerId; }
    public void setLeaderPlayerId(Long leaderPlayerId) { this.leaderPlayerId = leaderPlayerId; }
    public Long getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(Long targetPlayerId) { this.targetPlayerId = targetPlayerId; }
}
